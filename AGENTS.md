# AGENTS.md - Documentation for AI Agents

**Purpose:** Single source of truth for all AI coding assistants (Claude Code, Cursor AI, OpenAI Codex, Google Gemini, GitHub Copilot, and others). Ensures all agents work with consistent guidelines and patterns.

> `CLAUDE.md` in the repo root is a symlink to this file. Update **only** `AGENTS.md`.

## Detailed Documentation

**Comprehensive guides for specific tasks:**

| Category | Guide | Purpose |
|----------|-------|---------|
| Architecture | [Architecture](docs/ARCHITECTURE.md) | Source sets, expect/actual, Compose Multiplatform, module layout |
| Technologies | [Technologies](docs/TECHNOLOGIES.md) | Stack overview with versions and roles |
| Standards | [Standards](docs/STANDARDS.md) | Kotlin/Compose conventions, naming, import order, expect/actual rules |
| Commands | [Development Commands](docs/DEVELOPMENT_COMMANDS.md) | Gradle tasks, hot reload, single test runs |
| Testing | [Testing](docs/TESTING_GUIDE.md) | kotlin.test setup, common vs platform tests, conventions |
| Platforms | [Platforms](docs/PLATFORMS.md) | Per-platform notes: Android, iOS, Desktop JVM, Web JS, Web Wasm |
| Build & Deploy | [Build & Deploy](docs/BUILD_DEPLOY.md) | APK, IPA via Xcode, native installers, web bundle |
| i18n | [I18N Guide](docs/I18N_GUIDE.md) | Compose Multiplatform resources, adding languages |
| Performance | [Performance](docs/PERFORMANCE.md) | Compose recomposition, stable params, Wasm vs JS, R8 |
| Accessibility | [Accessibility](docs/ACCESSIBILITY.md) | Semantics, contrast, touch targets, TalkBack/VoiceOver |
| Security | [Security](docs/SECURITY.md) | Secrets, network TLS, secure storage, dependency hygiene |
| Documentation | [Documentation Guide](docs/DOCUMENTATION_GUIDE.md) | When and how to update docs |
| AI Agents | [Agent Onboarding](docs/AI_AGENT_ONBOARDING.md), [Agent Collaboration](docs/AI_AGENT_COLLAB.md) | Setup, handoff, coordination |
| Forking | [Fork Customization](docs/FORK_CUSTOMIZATION.md) | Step-by-step rebrand of the starter into a new product |
| Skills/Agents | [.claude/README.md](.claude/README.md) | Available Claude Code skills and agents for this repo |

## Project Overview

**KMPStarter** — Kotlin Multiplatform / Compose Multiplatform **starter template**. The shared `:composeApp` module produces apps for **Android, iOS (arm64 + simulator arm64), Desktop JVM, Web JS, and Web Wasm** from a single Compose UI written in `commonMain`.

The repository is intentionally minimal (Compose Multiplatform "Hello World" with a `Greeting` and a button) so it can be **forked and adapted to any product**. See [Fork Customization](docs/FORK_CUSTOMIZATION.md) for the rebrand checklist.

> **Forking this?** Every renameable identifier (project name, applicationId, namespace, mainClass, packageName, app display name, window title, web title, iOS framework name) is flagged in source with a `// FORK-RENAME:` comment. List them with `grep -rn 'FORK-RENAME' .` and walk [Fork Customization](docs/FORK_CUSTOMIZATION.md) before merging product code.

**Technology Stack** (full list with versions: [Technologies](docs/TECHNOLOGIES.md))

- **Kotlin 2.3.20** — Multiplatform language
- **Compose Multiplatform 1.10.3** — Shared declarative UI
- **Material 3 1.10.0-alpha05** — Design system
- **AndroidX Lifecycle 2.10.0** — `viewmodel-compose`, `runtime-compose`
- **Compose Hot Reload 1.0.0** — Live reload on Desktop JVM
- **AGP 8.11.2** — Android Gradle Plugin (compileSdk 36, minSdk 24, targetSdk 36)
- **Java 11** — Source/target compatibility
- **Gradle Wrapper** — `./gradlew`
- **kotlinx-coroutines-swing 1.10.2** — Desktop coroutine dispatcher
- **kotlin.test** — Unified test framework

## Project Structure

> Full tree and rationale: **[Architecture Guide](docs/ARCHITECTURE.md#project-structure)**

```
composeApp/
└── src/
    ├── commonMain/kotlin/com/xergioalex/kmpstarter/
    │   ├── App.kt              # Shared root composable — all UI starts here
    │   ├── Platform.kt         # expect interface for platform info
    │   └── Greeting.kt         # Shared business logic example
    ├── commonMain/composeResources/   # Shared images/strings (generated `Res`)
    ├── commonTest/kotlin/      # Shared kotlin.test tests
    ├── androidMain/            # Android: MainActivity, Platform.android.kt, AndroidManifest.xml, res/
    ├── iosMain/                # iOS: MainViewController(), Platform.ios.kt
    ├── jvmMain/                # Desktop: main.kt with Window { App() }, Platform.jvm.kt
    ├── webMain/                # Shared web entry: ComposeViewport { App() } + index.html
    ├── jsMain/                 # JS-only: Platform.js.kt
    └── wasmJsMain/             # Wasm-only: Platform.wasmJs.kt

iosApp/                         # Xcode project consuming the iOS framework `ComposeApp`
gradle/libs.versions.toml       # Single version catalog — pin all dependencies here
build.gradle.kts                # Root build (plugins declared, applied per module)
composeApp/build.gradle.kts     # The only Gradle subproject
settings.gradle.kts             # Repositories + TYPESAFE_PROJECT_ACCESSORS
gradle.properties               # JVM args, configuration cache, AndroidX flags
local.properties                # Android SDK path (gitignored)
docs/                           # Project documentation
.claude/                        # Claude Code skills, agents, and command reference
tmp/                            # Scratch workspace (git-ignored, see below)
```

## Temporary Workspace (`tmp/`)

The `tmp/` directory at the project root is a **git-ignored scratch space** for agents and developers.

**Use it for:**
- Temporary prompts, outputs, or drafts
- One-off analysis results, debug logs, build artifacts copied for inspection
- Throw-away `.kt` snippets you want to compile/test outside of `commonMain`

**Rules:**
- Everything inside `tmp/` is ignored by git (except `.gitkeep`)
- Do NOT store anything permanent or important here — it can be deleted at any time
- When a user asks for a temporary file, prompt output, or scratch artifact, **use `tmp/`**. Subdirectories are fine (e.g., `tmp/prompts/`, `tmp/analysis/`).

## CRITICAL: Mandatory Requirements

### 1. Language Standards

**ALL code, comments, identifiers, commit messages, and documentation MUST be in English.** User-facing strings can be localized via Compose resources (see [I18N Guide](docs/I18N_GUIDE.md)). Always update documentation after important changes.

### 2. Common Code First (MANDATORY)

When adding a feature, **default to writing it in `commonMain`**. Drop into a platform source set **only** when:

- You need an `actual` for an `expect` declaration in `commonMain`
- You need to call a platform-only API (Android `Context`, iOS `UIKit`, JVM `java.*`, browser `window`)
- You need a platform-specific dependency

If you find yourself duplicating logic across platform source sets, hoist it to `commonMain` behind an `expect`/`actual` boundary.

### 3. expect / actual Discipline (MANDATORY)

```kotlin
// commonMain/Platform.kt
interface Platform {
    val name: String
}
expect fun getPlatform(): Platform
```

```kotlin
// androidMain/Platform.android.kt
class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}
actual fun getPlatform(): Platform = AndroidPlatform()
```

**Rules:**

1. Every `expect` must have an `actual` in **every active target** — missing one breaks the build for that target only, often discovered late
2. `expect` declarations live in `commonMain`. Place each `actual` in the matching platform source set (`androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`)
3. Filenames follow `Foo.<platform>.kt` for `actual`s (e.g., `Platform.android.kt`)
4. Keep `expect` surface minimal — large APIs become a maintenance burden

Full rules: **[Standards Guide](docs/STANDARDS.md#expectactual)**.

### 4. Import Order Convention (MANDATORY)

Kotlin imports follow `kotlin.code.style=official` ordering — **alphabetical, no manual grouping, no wildcards**. Let the IDE format.

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.xergioalex.kmpstarter.Greeting
import org.jetbrains.compose.resources.painterResource
```

**Do not** add manual blank-line groupings between import categories — the configured Kotlin style is `official`, which sorts alphabetically without grouping.

### 5. Code Quality (MANDATORY)

This starter ships with the Kotlin compiler's built-in checks only. Recommended additions when forking:

```bash
./gradlew :composeApp:assemble        # Compile everything
./gradlew :composeApp:check           # Run checks (tests + lint when configured)
./gradlew :composeApp:lint            # Android lint (after adding lint config)
```

If you add **ktlint** or **detekt**, document the wired tasks in [Development Commands](docs/DEVELOPMENT_COMMANDS.md) and add the check to the Pre-Commit Checklist.

### 6. Testing

```bash
./gradlew :composeApp:allTests        # All targets that support tests
./gradlew :composeApp:jvmTest         # JVM only (fastest, recommended for TDD)
./gradlew :composeApp:testDebugUnitTest   # Android unit tests
./gradlew :composeApp:iosSimulatorArm64Test   # iOS simulator tests
```

Run a single test:

```bash
./gradlew :composeApp:jvmTest --tests "com.xergioalex.kmpstarter.ComposeAppCommonTest.example"
```

Tests live in `composeApp/src/commonTest/` (shared) and `composeApp/src/<platform>Test/` (platform-specific). Conventions: **[Testing Guide](docs/TESTING_GUIDE.md)**.

### 7. Multiplatform Resources (MANDATORY)

Use Compose Multiplatform resources — **not** per-platform asset folders — for any image/string/font that should be shared.

- Drop assets in `composeApp/src/commonMain/composeResources/{drawable,values,font,files}/`
- Access via the generated `kmpstarter.composeapp.generated.resources.Res` (e.g., `Res.drawable.compose_multiplatform`)
- Localized strings live under `values-<locale>/strings.xml` (e.g., `values-es/strings.xml`); read with `stringResource(Res.string.app_name)`

**Never** hardcode user-visible strings in composables — wrap them in `stringResource(...)`. Full workflow: **[I18N Guide](docs/I18N_GUIDE.md)**.

### 8. Performance-First Mindset (MANDATORY)

1. **Keep composables stable** — prefer immutable data classes and `@Stable` / `@Immutable` annotations to skip recomposition
2. **Hoist state** — read state at the lowest possible composable; pass values down, events up
3. **Use `remember` and `derivedStateOf`** — avoid recomputing on every recomposition
4. **Lazy lists for long content** — `LazyColumn` / `LazyRow`, never `Column` over a large list
5. **Wasm > JS** for production web — Wasm is faster and smaller; only fall back to JS for older browsers
6. **R8 for Android release** — currently `isMinifyEnabled = false`; flip on for production builds and add a ProGuard config
7. **Static iOS framework** — already configured (`isStatic = true`) for faster app startup

See **[Performance Guide](docs/PERFORMANCE.md)**.

### 9. Accessibility Standards (MANDATORY)

1. **Material 3 contrast tokens** — use `MaterialTheme.colorScheme.*` instead of hardcoded colors so dark/light themes inherit a11y-vetted contrast
2. **Touch targets** — minimum 48dp / 48pt; Material components default to this — don't shrink
3. **Content descriptions** — every `Image` / `Icon` representing meaningful content needs `contentDescription`. Decorative images use `contentDescription = null`
4. **Semantics modifier** — when building custom components, set `Modifier.semantics { ... }` so TalkBack/VoiceOver can announce them
5. **Click targets are buttons** — use `Modifier.clickable` only when the role is non-button; otherwise use `Button`/`IconButton` so semantics roles are correct
6. **RTL support** — already enabled (`android:supportsRtl="true"`); keep it on, prefer `Modifier.padding(start = ...)` over `paddingLeft`

See **[Accessibility Guide](docs/ACCESSIBILITY.md)**.

### 10. Hot Reload Workflow (Desktop)

The Compose Hot Reload Gradle plugin is applied. To iterate quickly:

```bash
./gradlew :composeApp:run    # Launch with hot reload enabled
```

Edit any composable in `commonMain` or `jvmMain` — the running window picks up the change without restart. Use this as your default inner-loop for UI work, even when the final target is Android or iOS.

## Shared Agent Coordination

Multiple AI agents collaborate on this codebase. When updating agent guidance, mirror changes across all relevant files. See **[AI Agent Collaboration](docs/AI_AGENT_COLLAB.md)**.

## Quick Commands

```bash
# Run / develop
./gradlew :composeApp:run                                # Desktop with hot reload
./gradlew :composeApp:wasmJsBrowserDevelopmentRun        # Web (Wasm)
./gradlew :composeApp:jsBrowserDevelopmentRun            # Web (JS)
./gradlew :composeApp:assembleDebug                      # Android debug APK
./gradlew :composeApp:installDebug                       # Install on connected device/emulator

# Test
./gradlew :composeApp:allTests                           # All targets
./gradlew :composeApp:jvmTest                            # JVM only (fastest)
./gradlew :composeApp:testDebugUnitTest                  # Android unit tests

# Build / package
./gradlew :composeApp:assembleRelease                    # Android release APK
./gradlew :composeApp:packageDistributionForCurrentOS    # Desktop installer (Dmg/Msi/Deb)
./gradlew :composeApp:wasmJsBrowserDistribution          # Production web bundle

# Maintenance
./gradlew clean                                          # Clean all build outputs
./gradlew :composeApp:dependencies                       # Inspect dependency graph
./gradlew --stop                                         # Stop the Gradle daemon
```

iOS builds run from Xcode (`iosApp/iosApp.xcodeproj`) or via the IDE's KMP run config. Full reference: **[Development Commands](docs/DEVELOPMENT_COMMANDS.md)**.

## Architecture Patterns

> Full patterns with code examples: **[Architecture Guide](docs/ARCHITECTURE.md)**

### 1. Single Shared Composable Root

Every platform mounts the same `App()` composable from `commonMain`:

- **Android** — `MainActivity.setContent { App() }`
- **iOS** — `MainViewController()` returns `ComposeUIViewController { App() }`, consumed by SwiftUI in `iosApp/iosApp/ContentView.swift`
- **Desktop** — `application { Window { App() } }` in `jvmMain/main.kt`
- **Web (JS + Wasm)** — `ComposeViewport { App() }` in `webMain/main.kt`

Add new screens **inside** `App()`, not by introducing a new platform-side entry point.

### 2. Platform Abstraction via expect/actual

`commonMain` defines an `expect` contract; each platform provides the `actual`. This is the canonical way to access platform APIs without leaking them into shared code. Pattern: **[Architecture → Platform abstraction](docs/ARCHITECTURE.md#platform-abstraction)**.

### 3. Version Catalog Single Source of Truth

All dependency versions live in `gradle/libs.versions.toml`. Reference them via the typesafe accessor (`libs.compose.material3`) — never inline a version string in `build.gradle.kts`. When upgrading, edit the catalog only.

### 4. ViewModel via androidx.lifecycle (Multiplatform)

`androidx-lifecycle-viewmodel-compose` and `runtime-compose` are wired in `commonMain`, so `ViewModel` and `viewModel()` work across all targets — including iOS. Use them for screen-scoped state instead of plain `remember`.

### 5. Resources Pipeline

Compose Multiplatform compiles `commonMain/composeResources/` into a generated `Res` object. Reference assets via `painterResource(Res.drawable.foo)` and `stringResource(Res.string.foo)` — same call site on every platform.

### 6. iOS Framework Bridge

`composeApp` exposes a static framework named `ComposeApp` to Xcode. The Swift side imports `ComposeApp` and calls `MainViewControllerKt.MainViewController()` (Kotlin top-level functions are exposed as `<File>Kt.<name>`).

## Documentation Standards

Update docs after: adding source sets, changing target list, bumping major dependency versions, adding npm/Gradle scripts, establishing patterns, adding `expect`/`actual` boundaries, adding new platform-specific resources. See **[Documentation Guide](docs/DOCUMENTATION_GUIDE.md)**.

## Common Mistakes to Avoid

### DON'T:

1. Put platform-specific code in `commonMain` (use `expect`/`actual` instead)
2. Forget to add an `actual` in **every** active target — the missing target build will fail late
3. Inline a version string in `build.gradle.kts` (edit `gradle/libs.versions.toml`)
4. Hardcode user-visible strings (use `stringResource(Res.string.*)`)
5. Use `Column` to render long lists (use `LazyColumn`)
6. Ship Android release with `isMinifyEnabled = false` for production (enable R8 + ProGuard)
7. Use `Modifier.clickable` on what is conceptually a button (use `Button`/`IconButton` so a11y semantics are correct)
8. Hardcode colors (use `MaterialTheme.colorScheme.*`)
9. Read `Build.VERSION.SDK_INT` or `UIKit` types from `commonMain` (gate them behind `expect`)
10. Skip the IDE's Kotlin formatter (`kotlin.code.style=official` is set in `gradle.properties`)
11. Commit `local.properties`, `*.iml`, or `xcuserdata/` (already in `.gitignore`)
12. Add a new dependency that exists for one target only without checking it has multiplatform variants
13. Refer to the iOS framework as anything other than `ComposeApp` (the baseName is wired to that string)
14. Update `CLAUDE.md` directly — it is a symlink to `AGENTS.md`. Edit `AGENTS.md`.

### DO:

1. Write new code in `commonMain` first; promote to platform source sets only when forced
2. Use `expect`/`actual` with the smallest possible surface area
3. Reference dependencies via the `libs.*` accessor
4. Wrap user-visible strings in `stringResource(...)` — even if you only support one language today
5. Keep composables stateless when possible; hoist state to a `ViewModel` for screen-level state
6. Run `./gradlew :composeApp:jvmTest` as the inner loop for non-UI logic — it's the fastest target
7. Use `./gradlew :composeApp:run` (Desktop hot reload) as the inner loop for UI work
8. Bump versions only in `gradle/libs.versions.toml`
9. Add tests in `commonTest` whenever the logic lives in `commonMain`
10. Use `MaterialTheme.colorScheme` and `MaterialTheme.typography` so dark mode and theming are automatic

## Pre-Commit Checklist

- [ ] All code, comments, and identifiers in English
- [ ] `./gradlew :composeApp:assemble` succeeds (compiles for all targets)
- [ ] `./gradlew :composeApp:allTests` passes
- [ ] If you added an `expect`, every active target has the matching `actual`
- [ ] If you added a dependency, it lives in `gradle/libs.versions.toml`
- [ ] User-visible strings go through `stringResource(...)`
- [ ] No hardcoded colors — uses `MaterialTheme.colorScheme.*`
- [ ] No `local.properties` / `*.iml` / `xcuserdata/` staged
- [ ] Documentation updated for any architectural change (new source set, new target, new pattern)
- [ ] Commit message in English (conventional format)

## Skills & Agents (Claude Code)

This repository ships with a `.claude/` directory containing slash-command skills and specialized agents tuned for KMP/Compose work. Full catalog: **[.claude/README.md](.claude/README.md)**.

**Skills (slash commands):**

- `/add-screen` — Add a new shared Compose screen wired into `App()`
- `/add-expect-actual` — Create an `expect` in `commonMain` plus `actual`s for every active target
- `/add-resource` — Add a string/drawable/font to `composeResources` (and translations if applicable)
- `/write-tests` — Author `commonTest` (or platform-specific) tests for the current change
- `/fix-build` — Diagnose and repair a failing Gradle build
- `/bump-deps` — Update `gradle/libs.versions.toml` safely (changelog + verification)
- `/add-platform-feature` — Add a feature that needs a real platform API (network, storage, sensor)
- `/fork-rebrand` — Walk a fresh fork through package, applicationId, namespace, bundle id, app name
- `/release-android`, `/release-ios`, `/release-desktop`, `/release-web` — Per-target release procedures

**Agents:**

- `kmp-architect` — Decides where new code lives (common vs platform), reviews `expect`/`actual` boundaries
- `compose-ui` — Builds and refactors composables, enforces stability/performance rules
- `platform-bridge` — Implements `actual`s for Android/iOS/JVM/JS/Wasm
- `test-author` — Writes unit/UI tests with kotlin.test
- `dependency-auditor` — Reviews catalog updates and multiplatform variant compatibility
- `release-engineer` — Owns build-time concerns (R8, ProGuard, signing, distribution)
- `doc-writer` — Keeps `AGENTS.md` and `docs/` in sync with code

### How to Invoke Commands

| Agent | Prefix | Example |
|-------|--------|---------|
| **Claude Code** | `/` (native) | `/add-screen` |
| **OpenAI Codex** | `#` | `#add-screen` |
| **Cursor AI** | `#` | `#add-screen` |
| **Gemini / others** | `#` | `#add-screen` |

> **Why `#` for non-Claude agents?** Most AI CLIs (Codex, Cursor) intercept `/` as their own system commands. Using `#` avoids interception. You can also write the command name in plain text: "run add-screen".

When a command is invoked (via `/`, `#`, or by name), the agent MUST:

1. **Look up** the command in **[.claude/README.md](.claude/README.md)** to find its skill file
2. **READ** the linked skill file completely
3. **FOLLOW** its step-by-step instructions exactly
4. **DO NOT** improvise or skip steps — the skill file IS the spec

## Conventional Commits

**Format:** `<type>: <description>`

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`

Examples:
- `feat: add task list screen with shared state`
- `fix: align iOS safe area in App.kt`
- `chore: bump compose multiplatform to 1.10.4`
- `build: enable R8 for android release`
- `docs: document hot reload workflow`
