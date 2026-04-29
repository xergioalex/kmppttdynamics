#!/usr/bin/env bash
# Build the production Wasm bundle for static hosting (Cloudflare
# Pages, GitHub Pages, S3, etc).
#
# Output:  composeApp/build/dist/wasmJs/productionExecutable/
#
# Designed to be the build command on Cloudflare Pages (and to be
# reproducible locally — see docs/BUILD_DEPLOY.md#web for the dashboard
# config). It expects Java 21 either via JAVA_HOME or installed
# system-wide; falls back to whatever `java` resolves to on $PATH if
# neither is available.
#
# Required environment variables (read by buildkonfig at compile time):
#   SUPABASE_URL                 https://<project-ref>.supabase.co
#   SUPABASE_PUBLISHABLE_KEY     anon-tier publishable key
#
# Both should be set in the Cloudflare Pages dashboard or in your
# local .env file (the Gradle build reads .env first, then env vars).

set -euo pipefail

DIST_DIR="composeApp/build/dist/wasmJs/productionExecutable"

# ─── Resolve JAVA_HOME ────────────────────────────────────────────────
# macOS dev box: prefer the JDK 21 installed via brew/sdkman.
# Linux CI (Cloudflare Pages, GitHub Actions): JAVA_HOME is typically
# set by the build image or via JAVA_VERSION=21 in the env config.
if [[ -z "${JAVA_HOME:-}" ]]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || echo "")"
    fi
fi
export JAVA_HOME

if [[ -n "${JAVA_HOME:-}" ]]; then
    echo "==> JAVA_HOME=$JAVA_HOME"
    "$JAVA_HOME/bin/java" -version 2>&1 | head -1
else
    echo "==> JAVA_HOME unset; using system 'java':"
    java -version 2>&1 | head -1
fi

# ─── Sanity-check the public Supabase config ──────────────────────────
if [[ -z "${SUPABASE_URL:-}" || -z "${SUPABASE_PUBLISHABLE_KEY:-}" ]]; then
    if [[ -f .env ]]; then
        echo "==> SUPABASE_* env vars unset; the Gradle build will read .env"
    else
        echo "WARNING: SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY are not set," >&2
        echo "         and there's no .env file in the repo root. The build" >&2
        echo "         will succeed but the deployed app will not be able to" >&2
        echo "         talk to Supabase — set both in the Cloudflare Pages" >&2
        echo "         dashboard before deploying." >&2
    fi
fi

# ─── Clean previous output so stale hashed chunks don't sneak in ──────
rm -rf "$DIST_DIR"

# ─── Build ────────────────────────────────────────────────────────────
echo "==> Running Gradle wasmJsBrowserDistribution..."
./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon --max-workers=2

# ─── Report what we produced ──────────────────────────────────────────
echo
echo "==> Bundle contents:"
ls -lh "$DIST_DIR"
echo
echo "==> Total uncompressed size:"
du -sh "$DIST_DIR"
echo
echo "✅ Static bundle ready at: $DIST_DIR"
echo
echo "Cloudflare Pages dashboard config:"
echo "  Build command:    bash scripts/build_web.sh"
echo "  Output directory: $DIST_DIR"
echo "  Environment:      SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, JAVA_VERSION=21"
