# KMPTodoApp

A cross-platform Todo app written **once in `commonMain`** with Kotlin Multiplatform + Compose Multiplatform, and shipped to **Android, iOS, Desktop (JVM), Web (Wasm), and Web (JS)** from a single shared module.

The point of this project is to exercise the real bondades of KMP — shared domain, shared UI, platform code only where it earns its keep — on a small but realistic product.

![KMPTodoApp running on the Android emulator alongside the source tree in Android Studio](assets/android_studio_setup.png)

## Features

- **Full CRUD** with title, notes, category, priority (Low / Medium / High), due date, and done flag
- **Filter** by *All / Active / Done* (filter persists across sessions) and **free-text search** over title / notes / category
- **Mark done with strikethrough**; the list re-sorts so active tasks come first, then by priority and due date
- **Material 3 date picker** for due dates
- **Theme**: System / Light / Dark — your choice persists per device
- **i18n out of the box**: English + Spanish; the app picks up the system locale
- **Adaptive layout**: single-pane on phones, list + detail on tablets / desktop / web (≥ 720 dp wide)
- **Native share** per platform: Android intent / iOS share sheet / Desktop clipboard / browser `navigator.clipboard`
- **Persistent storage** on Android, iOS and Desktop via SQLite (SQLDelight). Web is in-memory in v1 (see [App Overview → What's next](docs/APP_OVERVIEW.md#whats-next))

> **Want the full story** — feature list, source set layout, persistence backends per platform, and which KMP patterns each piece exercises? Read **[`docs/APP_OVERVIEW.md`](docs/APP_OVERVIEW.md)**.

## Architecture at a glance

```
commonMain          domain/   →   ui/   (StateFlow ViewModels, Compose screens, Material 3 theme)
                       ▲
                       │ implements
                       │
nonWebMain          data/SqlTaskRepository (SQLDelight)        ── android / ios / jvm
webMain             data/InMemoryTaskRepository (StateFlow)    ── jsMain   / wasmJsMain
```

- All UI, ViewModels, and the domain model live in `commonMain` — no per-platform clones.
- `TaskRepository` is one interface with two implementations split by an intermediate `nonWebMain` source set.
- Platform-only concerns (database driver, settings backend, native share) sit behind `expect`/`actual` or behind small per-platform classes injected via an `AppContainer` data class.

The whole thing reads like a normal Android Compose app with the platform glue moved outside `commonMain`. See [App Overview](docs/APP_OVERVIEW.md) and [Architecture](docs/ARCHITECTURE.md) for the long version.

## Tech stack

| What | Version | Why |
|---|---|---|
| **Kotlin Multiplatform** | 2.3.20 | Shared language across all targets |
| **Compose Multiplatform** | 1.10.3 | Shared declarative UI on every target |
| **Material 3** | 1.10.0-alpha05 | Design system, theming, date picker |
| **AndroidX Lifecycle (KMP)** | 2.10.0 | `ViewModel` + `viewModelScope` in `commonMain` |
| **SQLDelight** | 2.1.0 | Type-safe SQLite for Android / iOS / JVM |
| **kotlinx-datetime** | 0.7.1 | `LocalDateTime` + `TimeZone` formatting; pairs with `kotlin.time` for `Instant` / `Clock` |
| **multiplatform-settings** | 1.2.0 | Persistent key-value (theme, filter) on every target |
| **Compose Hot Reload** | 1.0.0 | Live reload while iterating on Desktop |

Full catalog with rationale: [docs/TECHNOLOGIES.md](docs/TECHNOLOGIES.md).

## Quick start

> ⚠️ Use **Java 21** for Gradle. The bundled Kotlin compiler in Gradle 8.14 doesn't yet handle newer JDKs. On macOS:
> ```bash
> export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
> ```
> When you run from Android Studio's Run config, the IDE supplies its own JDK and you don't need this export.

```bash
./gradlew :composeApp:run                              # Desktop (with Compose Hot Reload)
./gradlew :composeApp:installDebug                     # Android (needs an emulator/device)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun      # Web (Wasm — preferred)
./gradlew :composeApp:jsBrowserDevelopmentRun          # Web (JS — legacy fallback)
# iOS: open iosApp/iosApp.xcodeproj in Xcode and ⌘R
```

Full command reference: [docs/DEVELOPMENT_COMMANDS.md](docs/DEVELOPMENT_COMMANDS.md).

## Getting started (new to KMP?)

If this is your first Kotlin Multiplatform project on macOS, walk these in order:

1. [`docs/getting-started/ENVIRONMENT_SETUP.md`](docs/getting-started/ENVIRONMENT_SETUP.md) — install Android Studio, Xcode, the KMP plugin, and Java 21
2. [`docs/getting-started/RUNNING_THE_APP.md`](docs/getting-started/RUNNING_THE_APP.md) — run on Android (emulator + real device), iOS (simulator + real iPhone), Desktop, and Web
3. [`docs/getting-started/TROUBLESHOOTING.md`](docs/getting-started/TROUBLESHOOTING.md) — every issue we actually hit during setup, with fixes
4. [`docs/getting-started/FLUTTER_BONUS.md`](docs/getting-started/FLUTTER_BONUS.md) — optional Flutter setup + KMP-vs-Flutter comparison

## What's inside

- `composeApp/` — the only Gradle subproject. Shared UI in `commonMain`, SQL repository in `nonWebMain`, in-memory repository in `webMain`, platform glue in `androidMain` / `iosMain` / `jvmMain` / `jsMain` / `wasmJsMain`
- `iosApp/` — Xcode project that consumes the `ComposeApp` framework
- `gradle/libs.versions.toml` — single version catalog (every dependency pinned here)
- `docs/` — full documentation set (see below)
- `.claude/` — Claude Code skills and agents tuned for KMP work
- `AGENTS.md` — single source of truth for AI assistants (Claude, Cursor, Codex, Gemini, Copilot)

## Documentation

| Topic | File |
|---|---|
| **App overview, features, architecture** | [`docs/APP_OVERVIEW.md`](docs/APP_OVERVIEW.md) |
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

## Project history

Bootstrapped from [`xergioalex/kmpstarter`](https://github.com/xergioalex/kmpstarter). Renameable identifiers from the upstream starter are flagged in source with `// FORK-RENAME:` comments — `grep -rn 'FORK-RENAME' .` to list them.

## License

Released under the [MIT License](LICENSE) — free to use, modify, and distribute.

## Credits

Bootstrapped from the JetBrains [Kotlin Multiplatform / Compose Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) project wizard.
