#!/usr/bin/env bash
# scripts/supabase_reset.sh
# =============================================================
# Wipes every user-touchable row from the Supabase project, leaving
# the schema, indexes, RLS policies, and the realtime publication
# intact. Useful during development to start onboarding from scratch
# without re-applying every migration.
#
# What it deletes (in one TRUNCATE CASCADE):
#   - public.app_users      → cross-meetup profiles (display name + avatar)
#   - public.meetups        → cascades through every child table
#                              (meetup_participants, chat_messages,
#                               raised_hands, questions / votes,
#                               polls / options / votes,
#                               raffles / entries / winners,
#                               activity_events)
#   - public.profiles       → reserved for future Supabase Auth users
#
# What it does NOT touch:
#   - Schema, indexes, RLS policies, realtime publication
#   - The migrations themselves (they're in supabase/migrations/)
#
# Local app data (multiplatform-settings) lives ONLY on each device
# and is not affected by this script. After running it you also want:
#   adb shell pm clear com.xergioalex.kmppttdynamics    # Android
#   xcrun simctl uninstall booted com.xergioalex.kmppttdynamics  # iOS
# (or in the simulator: long-press app icon → Remove App)
#
# Usage:
#   ./scripts/supabase_reset.sh
#
# The script is non-interactive: it prints counts before / after.
# Re-running it on an already-empty database is a no-op.
#
# Connection setup mirrors scripts/supabase_apply.sh — sources
# `.env`, falls back to building the URL from SUPABASE_PROJECT_REF +
# SUPABASE_DB_PASSWORD, and resolves IPv6 by hand on macOS hosts
# whose getaddrinfo() refuses to surface AAAA records.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$REPO_ROOT/.env"

if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    set -a
    . "$ENV_FILE"
    set +a
fi

# ─── Locate psql ─────────────────────────────────────────────────────
if ! command -v psql >/dev/null 2>&1; then
    for candidate in /opt/homebrew/opt/libpq/bin /usr/local/opt/libpq/bin; do
        if [[ -x "$candidate/psql" ]]; then
            export PATH="$candidate:$PATH"
            break
        fi
    done
fi
if ! command -v psql >/dev/null 2>&1; then
    echo "error: psql not found on PATH." >&2
    echo "       Install: brew install libpq && brew link --force libpq" >&2
    exit 1
fi

# ─── Build SUPABASE_DB_URL if not set ────────────────────────────────
if [[ -z "${SUPABASE_DB_URL:-}" ]]; then
    if [[ -n "${SUPABASE_PROJECT_REF:-}" && -n "${SUPABASE_DB_PASSWORD:-}" ]]; then
        ENCODED_PWD="$(python3 - <<PY
import sys, urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=''))
PY
"$SUPABASE_DB_PASSWORD")"
        SUPABASE_DB_URL="postgresql://postgres:${ENCODED_PWD}@db.${SUPABASE_PROJECT_REF}.supabase.co:5432/postgres"
    else
        echo "error: set SUPABASE_DB_URL (Session Pooler URL preferred) or" >&2
        echo "       SUPABASE_PROJECT_REF + SUPABASE_DB_PASSWORD in $ENV_FILE." >&2
        exit 1
    fi
fi

# ─── DNS fallback for IPv6-only direct hosts ─────────────────────────
if ! psql "$SUPABASE_DB_URL" -c 'select 1' >/dev/null 2>&1; then
    err="$(psql "$SUPABASE_DB_URL" -c 'select 1' 2>&1 || true)"
    if echo "$err" | grep -q "could not translate host name"; then
        host="$(echo "$SUPABASE_DB_URL" | sed -E 's|.*@([^:/?]+).*|\1|')"
        ipv6=""
        if command -v dscacheutil >/dev/null 2>&1; then
            ipv6="$(dscacheutil -q host -a name "$host" 2>/dev/null \
                | awk '/ipv6_address/ {print $2; exit}')"
        fi
        if [[ -z "$ipv6" ]] && command -v dig >/dev/null 2>&1; then
            ipv6="$(dig +short AAAA "$host" 2>/dev/null | head -1)"
        fi
        if [[ -n "$ipv6" ]]; then
            echo "warn: getaddrinfo() failed for $host but it has an IPv6 record." >&2
            echo "      Falling back to literal [$ipv6]." >&2
            SUPABASE_DB_URL="$(echo "$SUPABASE_DB_URL" | sed -E "s|@$host|@[$ipv6]|")"
            if [[ "$SUPABASE_DB_URL" != *sslmode=* ]]; then
                if [[ "$SUPABASE_DB_URL" == *\?* ]]; then
                    SUPABASE_DB_URL="${SUPABASE_DB_URL}&sslmode=require"
                else
                    SUPABASE_DB_URL="${SUPABASE_DB_URL}?sslmode=require"
                fi
            fi
        else
            echo "error: cannot resolve $host." >&2
            exit 1
        fi
    else
        echo "error: cannot connect to Postgres." >&2
        echo "$err" | sed 's/^/  /' >&2
        exit 1
    fi
fi

# ─── Run the truncate ────────────────────────────────────────────────
psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 <<'SQL'
\echo === BEFORE ===
select (select count(*) from public.app_users)              as app_users,
       (select count(*) from public.meetups)                as meetups,
       (select count(*) from public.meetup_participants)    as participants,
       (select count(*) from public.chat_messages)          as chat_messages,
       (select count(*) from public.raised_hands)           as hands,
       (select count(*) from public.questions)              as questions,
       (select count(*) from public.polls)                  as polls,
       (select count(*) from public.raffles)                as raffles,
       (select count(*) from public.activity_events)        as activity;

\echo === TRUNCATE (CASCADE) ===
truncate table
    public.app_users,
    public.meetups,
    public.profiles
restart identity cascade;

\echo === AFTER ===
select (select count(*) from public.app_users)              as app_users,
       (select count(*) from public.meetups)                as meetups,
       (select count(*) from public.meetup_participants)    as participants,
       (select count(*) from public.chat_messages)          as chat_messages,
       (select count(*) from public.raised_hands)           as hands,
       (select count(*) from public.questions)              as questions,
       (select count(*) from public.polls)                  as polls,
       (select count(*) from public.raffles)                as raffles,
       (select count(*) from public.activity_events)        as activity;
SQL

echo "done."
