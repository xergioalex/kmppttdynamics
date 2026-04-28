#!/usr/bin/env bash
# Applies every migration in supabase/migrations/ in lexical order against
# the database identified by SUPABASE_DB_URL — or, if that's not set,
# constructed from SUPABASE_PROJECT_REF + SUPABASE_DB_PASSWORD.
#
# Usage:
#   1. cp .env.example .env  &&  fill in the values
#   2. ./scripts/supabase_apply.sh
#
# Requires: psql on PATH (Homebrew libpq is auto-detected).
#
# Networking note: Supabase's direct database host
# (db.<ref>.supabase.co) is IPv6-only. macOS's getaddrinfo() sometimes
# refuses to surface the AAAA record even when IPv6 is otherwise
# working, breaking psql's hostname resolution. When that happens, this
# script falls back to the literal IPv6 address. The proper long-term
# fix is to use the Session Pooler URL, which is IPv4+IPv6 — see
# README.md → Supabase setup.

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
# Homebrew's `libpq` formula is keg-only, so users who installed it
# with `brew install libpq` (without `brew link --force libpq`) won't
# have psql on PATH. Auto-detect the standard locations.
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
# Try a one-shot connection. If hostname resolution fails but the host
# has an AAAA record, retry with the IPv6 literal.
if ! psql "$SUPABASE_DB_URL" -c 'select 1' >/dev/null 2>&1; then
    err="$(psql "$SUPABASE_DB_URL" -c 'select 1' 2>&1 || true)"
    if echo "$err" | grep -q "could not translate host name"; then
        # Pull the host between '@' and the next ':' or '/'.
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
            echo "      For a permanent fix, switch SUPABASE_DB_URL in .env to the" >&2
            echo "      Session Pooler URL (Project Settings → Database → Session pooler)." >&2
            SUPABASE_DB_URL="$(echo "$SUPABASE_DB_URL" | sed -E "s|@$host|@[$ipv6]|")"
            # The pooler / direct hosts both require SSL; force it on
            # if the URL didn't already specify a mode.
            if [[ "$SUPABASE_DB_URL" != *sslmode=* ]]; then
                if [[ "$SUPABASE_DB_URL" == *\?* ]]; then
                    SUPABASE_DB_URL="${SUPABASE_DB_URL}&sslmode=require"
                else
                    SUPABASE_DB_URL="${SUPABASE_DB_URL}?sslmode=require"
                fi
            fi
            if ! psql "$SUPABASE_DB_URL" -c 'select 1' >/dev/null 2>&1; then
                echo "error: IPv6 fallback also failed." >&2
                psql "$SUPABASE_DB_URL" -c 'select 1' 2>&1 | sed 's/^/  /' >&2
                exit 1
            fi
        else
            echo "error: cannot resolve $host (no A or AAAA record found)." >&2
            exit 1
        fi
    else
        echo "error: cannot connect to Postgres." >&2
        echo "$err" | sed 's/^/  /' >&2
        exit 1
    fi
fi

# ─── Apply every migration in lexical order ──────────────────────────
shopt -s nullglob
migrations=( "$REPO_ROOT"/supabase/migrations/*.sql )
if [[ ${#migrations[@]} -eq 0 ]]; then
    echo "no migrations found in supabase/migrations/" >&2
    exit 1
fi

for f in "${migrations[@]}"; do
    echo "applying $(basename "$f") ..."
    psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f "$f"
done

echo "done."
