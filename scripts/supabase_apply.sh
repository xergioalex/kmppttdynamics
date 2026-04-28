#!/usr/bin/env bash
# Applies every migration in supabase/migrations/ in lexical order against
# the database identified by SUPABASE_DB_URL — or, if that's not set,
# constructed from SUPABASE_PROJECT_REF + SUPABASE_DB_PASSWORD.
#
# Usage:
#   1. cp .env.example .env  &&  fill in the values
#   2. ./scripts/supabase_apply.sh
#
# Requires: psql on PATH.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$REPO_ROOT/.env"

if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    set -a
    . "$ENV_FILE"
    set +a
fi

if [[ -z "${SUPABASE_DB_URL:-}" ]]; then
    if [[ -n "${SUPABASE_PROJECT_REF:-}" && -n "${SUPABASE_DB_PASSWORD:-}" ]]; then
        # URL-encode the password to survive special characters.
        ENCODED_PWD="$(python3 - <<PY
import sys, urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=''))
PY
"$SUPABASE_DB_PASSWORD")"
        SUPABASE_DB_URL="postgresql://postgres:${ENCODED_PWD}@db.${SUPABASE_PROJECT_REF}.supabase.co:5432/postgres"
    else
        echo "error: set SUPABASE_DB_URL (preferred) or" >&2
        echo "       SUPABASE_PROJECT_REF + SUPABASE_DB_PASSWORD in $ENV_FILE." >&2
        exit 1
    fi
fi

if ! command -v psql >/dev/null 2>&1; then
    echo "error: psql not found on PATH. Install the postgres client (e.g. brew install libpq)." >&2
    exit 1
fi

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
