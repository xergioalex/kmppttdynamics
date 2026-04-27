# KMPStarter

A Kotlin Multiplatform / Compose Multiplatform **starter template** targeting Android, iOS, Desktop (JVM), Web (Wasm), and Web (JS) from a single shared `composeApp` module.

> **You're forking this?** Walk through [`docs/FORK_CUSTOMIZATION.md`](docs/FORK_CUSTOMIZATION.md) before writing product code. Every renameable identifier is also flagged in source with `// FORK-RENAME:` comments — `grep -rn 'FORK-RENAME'` to list them.

## Getting started (new to KMP?)

If this is your first Kotlin Multiplatform project on macOS, walk these in order:

1. [`docs/getting-started/ENVIRONMENT_SETUP.md`](docs/getting-started/ENVIRONMENT_SETUP.md) — install Android Studio, Xcode, the KMP plugin, and Java 21
2. [`docs/getting-started/RUNNING_THE_APP.md`](docs/getting-started/RUNNING_THE_APP.md) — run on Android (emulator + real device), iOS (simulator + real iPhone), Desktop, and Web
3. [`docs/getting-started/TROUBLESHOOTING.md`](docs/getting-started/TROUBLESHOOTING.md) — every issue we actually hit during setup, with fixes
4. [`docs/getting-started/FLUTTER_BONUS.md`](docs/getting-started/FLUTTER_BONUS.md) — optional Flutter setup + KMP-vs-Flutter comparison

Already set up? Skip to the quick start below.

## Quick start

```bash
./gradlew :composeApp:run                              # Desktop (with Compose Hot Reload)
./gradlew :composeApp:installDebug                     # Android (needs an emulator/device)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun      # Web (Wasm — preferred)
./gradlew :composeApp:jsBrowserDevelopmentRun          # Web (JS — legacy fallback)
# iOS: open iosApp/iosApp.xcodeproj in Xcode and ⌘R
```

Full command reference: [`docs/DEVELOPMENT_COMMANDS.md`](docs/DEVELOPMENT_COMMANDS.md).

## What's inside

- `composeApp/` — the only Gradle subproject. Shared UI in `commonMain`, platform glue in `androidMain` / `iosMain` / `jvmMain` / `jsMain` / `wasmJsMain`, shared web entry in `webMain`
- `iosApp/` — Xcode project that consumes the `ComposeApp` framework
- `gradle/libs.versions.toml` — single version catalog (every dependency pinned here)
- `docs/` — full documentation set
- `.claude/` — Claude Code skills and agents tuned for KMP work
- `AGENTS.md` — single source of truth for AI assistants (Claude, Cursor, Codex, Gemini, Copilot)

## Documentation

| Topic | File |
|---|---|
| Getting started (macOS, from zero) | [`docs/getting-started/`](docs/getting-started/) |
| Architecture & source sets | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| Stack and versions | [`docs/TECHNOLOGIES.md`](docs/TECHNOLOGIES.md) |
| Coding standards | [`docs/STANDARDS.md`](docs/STANDARDS.md) |
| Gradle commands | [`docs/DEVELOPMENT_COMMANDS.md`](docs/DEVELOPMENT_COMMANDS.md) |
| Testing | [`docs/TESTING_GUIDE.md`](docs/TESTING_GUIDE.md) |
| Per-platform notes | [`docs/PLATFORMS.md`](docs/PLATFORMS.md) |
| Build & deploy | [`docs/BUILD_DEPLOY.md`](docs/BUILD_DEPLOY.md) |
| Internationalization | [`docs/I18N_GUIDE.md`](docs/I18N_GUIDE.md) |
| Performance | [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md) |
| Accessibility | [`docs/ACCESSIBILITY.md`](docs/ACCESSIBILITY.md) |
| Security | [`docs/SECURITY.md`](docs/SECURITY.md) |
| Fork rebrand checklist | [`docs/FORK_CUSTOMIZATION.md`](docs/FORK_CUSTOMIZATION.md) |
| AI agent onboarding | [`docs/AI_AGENT_ONBOARDING.md`](docs/AI_AGENT_ONBOARDING.md) |

## License

No license is shipped with this starter. Add one before publishing your fork — typical choices: MIT, Apache-2.0, or proprietary (no LICENSE file).

## Credits

Bootstrapped from the JetBrains [Kotlin Multiplatform / Compose Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) project wizard.
