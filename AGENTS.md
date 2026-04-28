# AGENTS.md - Documentation for AI Agents

**Purpose:** Single source of truth for all AI coding assistants (Claude Code, Cursor AI, OpenAI Codex, Google Gemini, GitHub Copilot, and others). Ensures all agents work with consistent guidelines and patterns.

> `CLAUDE.md` in the repo root is a symlink to this file. Update **only** `AGENTS.md`.

## Detailed Documentation

**Comprehensive guides for specific tasks:**

| Category | Guide | Purpose |
|----------|-------|---------|
| **App overview** | [App Overview](docs/APP_OVERVIEW.md) | Feature roadmap, milestone plan, KMP patterns demonstrated |
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
| Security | [Security](docs/SECURITY.md) | Secrets, network TLS, RLS, dependency hygiene |
| Documentation | [Documentation Guide](docs/DOCUMENTATION_GUIDE.md) | When and how to update docs |
| AI Agents | [Agent Onboarding](docs/AI_AGENT_ONBOARDING.md), [Agent Collaboration](docs/AI_AGENT_COLLAB.md) | Setup, handoff, coordination |
| Skills/Agents | [.claude/README.md](.claude/README.md) | Available Claude Code skills and agents for this repo |

## Project Overview

**KMPPTTDynamics** (product name **Pereira Tech Talks Dynamics**, "PTT Dynamics" on the home screen) — a realtime meetup engagement app built with Kotlin Multiplatform and Compose Multiplatform. The shared `:composeApp` module produces apps for **Android, iOS (arm64 + simulator arm64), Desktop JVM, Web JS, and Web Wasm** from a single Compose UI written in `commonMain`.

**Core idea:** every meetup is a realtime room. The host activates dynamics — chat, raised hands, Q&A, polls, raffles, trivia, announcements — and all connected devices update instantly via Supabase Realtime.

**Milestone 1 (current) implements:**
- Create / list / join meetups
- Realtime participant list inside the room
- Host controls (start / pause / end)
- 6-character join codes that exclude visually ambiguous glyphs (0/O/1/I)

Subsequent milestones layer chat, hand-raise, Q&A, polls, raffles, trivia, and reactions.

> Read [App Overview](docs/APP_OVERVIEW.md) for the full milestone plan and which KMP patterns each piece exercises.

**Technology Stack** (full list with versions: [Technologies](docs/TECHNOLOGIES.md))

- **Kotlin 2.3.20** — Multiplatform language
- **Compose Multiplatform 1.10.3** — Shared declarative UI
- **Material 3 1.10.0-alpha05** — Design system
- **AndroidX Lifecycle 2.10.0** — `viewmodel-compose`, `runtime-compose`
- **supabase-kt 3.6.0** — Postgrest queries + Realtime subscriptions
- **Ktor 3.0.3** — Transport for supabase-kt (CIO on Android/JVM, Darwin on iOS, JS engine on Web)
- **kotlinx-serialization 1.7.3** — JSON encoding for Postgrest
- **kotlinx-coroutines 1.10.2** — Core + `kotlinx-coroutines-swing` (Desktop dispatcher)
- **BuildKonfig 0.17.1** — Generates a multiplatform `BuildConfig` from `.env` values
- **multiplatform-settings 1.2.0** — Persistent key-value backed by `SharedPreferences` / `NSUserDefaults` / `java.util.prefs` / `localStorage`
- **kotlinx-datetime 0.7.1** — `LocalDateTime` + `TimeZone` formatting; pairs with `kotlin.time.Instant`
- **Compose Hot Reload 1.0.0** — Live reload on Desktop JVM
- **AGP 8.11.2** — Android Gradle Plugin (compileSdk 36, minSdk 24, targetSdk 36)
- **Java 11** — Source/target compatibility (build with **JDK 21** — Gradle 8.14 doesn't yet recognize newer JDKs)
- **kotlin.test** — Unified test framework

> SQLDelight was used by the previous Todo iteration of this repo. It has been removed for Milestone 1 because the Meetup data model is inherently online-required (Supabase). An offline cache is a documented Milestone 6+ follow-up.

## Project Structure

> Full tree and rationale: **[Architecture Guide](docs/ARCHITECTURE.md#project-structure)**

```
composeApp/
└── src/
    ├── commonMain/kotlin/com/xergioalex/kmppttdynamics/
    │   ├── App.kt                         # Shared root + state-based routing
    │   ├── AppContainer.kt                # DI-lite holder (settings + lazy repos)
    │   ├── JoinCodeGenerator.kt           # Stage-friendly 6-char codes
    │   ├── domain/                        # Meetup, MeetupParticipant, MeetupStatus, ParticipantRole
    │   ├── supabase/                      # SupabaseClientProvider (lazy, BuildKonfig-driven)
    │   ├── meetups/                       # MeetupRepository (REST + realtime channel)
    │   ├── participants/                  # ParticipantRepository (REST + realtime channel)
    │   ├── settings/AppSettings.kt        # theme + last display name
    │   └── ui/{home,create,join,room,theme,components}
    ├── commonMain/composeResources/
    │   ├── drawable/{ptt_logo_vertical.png, ptt_logo_horizontal.png}
    │   ├── values/strings.xml             # i18n EN
    │   └── values-es/strings.xml          # i18n ES
    ├── commonTest/                        # kotlin.test
    ├── androidMain/                       # MainActivity, Platform.android.kt, launcher icons (PTT logo)
    ├── iosMain/                           # MainViewController, Platform.ios.kt
    ├── jvmMain/                           # main.kt + Platform.jvm.kt
    ├── webMain/                           # ComposeViewport entry shared by JS + Wasm
    ├── jsMain/Platform.js.kt
    └── wasmJsMain/Platform.wasmJs.kt

iosApp/                          # Xcode project consuming the ComposeApp framework
supabase/migrations/             # Idempotent SQL — apply with ./scripts/supabase_apply.sh
scripts/supabase_apply.sh
.env.example                     # Template — copy to .env (gitignored)
gradle/libs.versions.toml        # Single version catalog — pin all dependencies here
build.gradle.kts                 # Root build (plugins declared, applied per module)
composeApp/build.gradle.kts      # The only Gradle subproject + BuildKonfig wiring
settings.gradle.kts              # rootProject.name = "KMPPTTDynamics"
gradle.properties                # JVM args, configuration cache, AndroidX flags
local.properties                 # Android SDK path (gitignored)
docs/                            # Project documentation
.claude/                         # Claude Code skills, agents, and command reference
assets/pereiratechtalks/         # Source logos used for branding
tmp/                             # Scratch workspace (git-ignored)
```

## Temporary Workspace (`tmp/`)

Scratch space for agents and developers. Everything inside `tmp/` is git-ignored (except `.gitkeep`). Don't keep anything important there.

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

Every `expect` must have an `actual` in **every active target**. Filenames follow `Foo.<platform>.kt` (e.g., `Platform.android.kt`). Keep `expect` surface minimal — large APIs become a maintenance burden.

Full rules: **[Standards Guide](docs/STANDARDS.md#expectactual)**.

### 4. Import Order Convention (MANDATORY)

Kotlin imports follow `kotlin.code.style=official` ordering — **alphabetical, no manual grouping, no wildcards**. Let the IDE format.

### 5. Code Quality (MANDATORY)

This project ships with the Kotlin compiler's built-in checks only:

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :composeApp:assemble    # Compile everything
./gradlew :composeApp:check                                                    # Run checks
```

If you add **ktlint** or **detekt**, document the wired tasks in [Development Commands](docs/DEVELOPMENT_COMMANDS.md) and add the check to the Pre-Commit Checklist.

### 6. Testing

```bash
./gradlew :composeApp:allTests              # All targets that support tests
./gradlew :composeApp:jvmTest               # JVM only (fastest, recommended for TDD)
./gradlew :composeApp:testDebugUnitTest     # Android unit tests
./gradlew :composeApp:iosSimulatorArm64Test # iOS simulator tests
```

Run a single test:

```bash
./gradlew :composeApp:jvmTest --tests "com.xergioalex.kmppttdynamics.ComposeAppCommonTest.example"
```

Tests live in `composeApp/src/commonTest/` (shared) and `composeApp/src/<platform>Test/` (platform-specific). Conventions: **[Testing Guide](docs/TESTING_GUIDE.md)**.

### 7. Multiplatform Resources (MANDATORY)

Use Compose Multiplatform resources — **not** per-platform asset folders — for any image/string/font that should be shared.

- Drop assets in `composeApp/src/commonMain/composeResources/{drawable,values,font,files}/`
- Access via the generated `kmppttdynamics.composeapp.generated.resources.Res` (e.g., `Res.drawable.ptt_logo_vertical`)
- Localized strings live under `values-<locale>/strings.xml` (e.g., `values-es/strings.xml`); read with `stringResource(Res.string.app_title)`

**Never** hardcode user-visible strings in composables — wrap them in `stringResource(...)`. Full workflow: **[I18N Guide](docs/I18N_GUIDE.md)**.

### 8. Realtime Discipline (MANDATORY)

Every repository that powers a live UI exposes a `Flow<…>` that:

1. Subscribes to a `supabase_realtime` channel filtered by `meetup_id`.
2. Emits an initial REST snapshot.
3. Re-emits a fresh snapshot on every Postgres change.
4. **Unsubscribes** the channel when the flow is cancelled (use `try { … } finally { withContext(NonCancellable) { channel.unsubscribe() } }`).

Existing examples: `MeetupRepository.observeAll()`, `ParticipantRepository.observe(meetupId)`. Match this pattern for chat, hand-raise, Q&A, polls, raffles.

### 9. Supabase Trust Boundaries (MANDATORY)

| Variable | Purpose | May appear in |
|---|---|---|
| `SUPABASE_URL` | base project URL — `https://<ref>.supabase.co`, **never** the `/rest/v1/` endpoint | `commonMain` (via BuildKonfig), `.env` |
| `SUPABASE_PUBLISHABLE_KEY` | anon-tier public key, gated by RLS | `commonMain` (via BuildKonfig), `.env` |
| `SUPABASE_PROJECT_REF` | project subdomain — used by `supabase` CLI / migration script | `.env`, CLI scripts |
| `SUPABASE_ACCESS_TOKEN` | personal account token for the CLI | `.env`, CLI scripts |
| `SUPABASE_DB_PASSWORD` | direct Postgres credential | `.env`, CLI scripts |
| `SUPABASE_DB_URL` | full `postgresql://…` connection string | `.env`, CLI scripts |
| `SUPABASE_SECRET_KEY` | service-role key — backend / admin scripts only | `.env`, server-side only |

**Rules:**
- Only the first two ever leave `.env` to enter the app. They flow in via BuildKonfig and are exposed as `BuildConfig.SUPABASE_URL` / `BuildConfig.SUPABASE_PUBLISHABLE_KEY` to `commonMain`.
- The rest stay on developer laptops and trusted CI/CD secrets — never in `commonMain`, app resources, generated config, or committed code.
- The publishable key is safe in clients **only because RLS is configured**. Tighten the MVP-permissive RLS in `supabase/migrations/001_init.sql` before going to production.

To apply database migrations:

```bash
./scripts/supabase_apply.sh
```

Migrations live under `supabase/migrations/` and are idempotent (`CREATE … IF NOT EXISTS`). The script accepts either `SUPABASE_DB_URL` directly or constructs one from `SUPABASE_PROJECT_REF` + `SUPABASE_DB_PASSWORD`.

### 10. Performance-First Mindset (MANDATORY)

1. **Keep composables stable** — prefer immutable data classes and `@Stable` / `@Immutable` annotations to skip recomposition
2. **Hoist state** — read state at the lowest possible composable; pass values down, events up
3. **Use `remember` and `derivedStateOf`** — avoid recomputing on every recomposition
4. **Lazy lists for long content** — `LazyColumn` / `LazyRow`, never `Column` over a large list
5. **Wasm > JS** for production web — Wasm is faster and smaller; only fall back to JS for older browsers
6. **R8 for Android release** — currently `isMinifyEnabled = false`; flip on for production builds and add a ProGuard config
7. **Static iOS framework** — already configured (`isStatic = true`) for faster app startup

See **[Performance Guide](docs/PERFORMANCE.md)**.

### 11. Accessibility Standards (MANDATORY)

1. **Material 3 contrast tokens** — use `MaterialTheme.colorScheme.*` instead of hardcoded colors so dark/light themes inherit a11y-vetted contrast
2. **Touch targets** — minimum 48dp / 48pt; Material components default to this — don't shrink
3. **Content descriptions** — every `Image` / `Icon` representing meaningful content needs `contentDescription`. Decorative images use `contentDescription = null`
4. **Semantics modifier** — when building custom components, set `Modifier.semantics { ... }` so TalkBack/VoiceOver can announce them
5. **Click targets are buttons** — use `Modifier.clickable` only when the role is non-button; otherwise use `Button`/`IconButton` so semantics roles are correct
6. **RTL support** — already enabled (`android:supportsRtl="true"`); keep it on, prefer `Modifier.padding(start = ...)` over `paddingLeft`

See **[Accessibility Guide](docs/ACCESSIBILITY.md)**.

### 12. Hot Reload Workflow (Desktop)

The Compose Hot Reload Gradle plugin is applied. To iterate quickly:

```bash
./gradlew :composeApp:run    # Launch with hot reload enabled
```

Edit any composable in `commonMain` or `jvmMain` — the running window picks up the change without restart. Use this as your default inner-loop for UI work, even when the final target is Android or iOS.

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

# Supabase
./scripts/supabase_apply.sh                              # Apply SQL migrations

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

- **Android** — `MainActivity.setContent { App(container) }`
- **iOS** — `MainViewController()` returns `ComposeUIViewController { App(container) }`
- **Desktop** — `application { Window { App(container) } }` in `jvmMain/main.kt`
- **Web (JS + Wasm)** — `ComposeViewport { App(container) }` in `webMain/main.kt`

Add new screens **inside** `App()`, not by introducing a new platform-side entry point.

### 2. Platform Abstraction via expect/actual

`commonMain` defines an `expect` contract; each platform provides the `actual`. Currently used for `Platform.name`. New platform-only concerns (deep-link handling, push notifications, native share for the join code) follow the same pattern.

### 3. Version Catalog Single Source of Truth

All dependency versions live in `gradle/libs.versions.toml`. Reference them via the typesafe accessor (`libs.compose.material3`) — never inline a version string in `build.gradle.kts`.

### 4. ViewModel via androidx.lifecycle (Multiplatform)

`androidx-lifecycle-viewmodel-compose` and `runtime-compose` are wired in `commonMain`, so `ViewModel` and `viewModel { … }` work across all targets — including iOS. Use them for screen-scoped state instead of plain `remember`.

### 5. Resources Pipeline

Compose Multiplatform compiles `commonMain/composeResources/` into a generated `Res` object. Reference assets via `painterResource(Res.drawable.ptt_logo_vertical)` and `stringResource(Res.string.app_title)`.

### 6. iOS Framework Bridge

`composeApp` exposes a static framework named `ComposeApp` to Xcode. The Swift side imports `ComposeApp` and calls `MainViewControllerKt.MainViewController()`.

### 7. Supabase Realtime Flow

Every realtime feed follows the same shape:

```kotlin
fun observe(meetupId: String): Flow<List<X>> = flow {
    val channel = supabase.channel("x_$meetupId")
    val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "x"
        filter("meetup_id", FilterOperator.EQ, meetupId)
    }
    channel.subscribe()
    try {
        emit(fetch(meetupId))
        changes.collect { emit(fetch(meetupId)) }
    } finally {
        withContext(NonCancellable) { channel.unsubscribe() }
    }
}
```

## Documentation Standards

Update docs after: adding source sets, changing target list, bumping major dependency versions, adding npm/Gradle scripts, establishing patterns, adding `expect`/`actual` boundaries, adding new platform-specific resources, **adding a new realtime feed or table**. See **[Documentation Guide](docs/DOCUMENTATION_GUIDE.md)**.

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
11. Commit `local.properties`, `*.iml`, `xcuserdata/`, **or `.env`** (already in `.gitignore`)
12. Add a new dependency that exists for one target only without checking it has multiplatform variants
13. Refer to the iOS framework as anything other than `ComposeApp`
14. Update `CLAUDE.md` directly — it is a symlink to `AGENTS.md`. Edit `AGENTS.md`.
15. Reference `SUPABASE_SECRET_KEY`, `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`, or `auth.users` from `commonMain` or any client-shipped code.
16. Subscribe to a Supabase Realtime channel without an `unsubscribe()` in a `finally` block — leaks pile up fast.
17. Add `@JsExport`-style configuration to BuildKonfig (`exposeObjectWithName`); Kotlin/Wasm rejects it on standalone objects.
18. Put `/rest/v1/` in `SUPABASE_URL` — the SDK appends paths itself.

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
11. Scope every realtime subscription to a `meetup_id` filter and unsubscribe in `finally`
12. Treat the publishable key as configuration, not a secret — but treat the rest of `.env` as secrets

## Pre-Commit Checklist

- [ ] All code, comments, and identifiers in English
- [ ] `JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :composeApp:assemble` succeeds (compiles for all targets)
- [ ] `./gradlew :composeApp:allTests` passes
- [ ] If you added an `expect`, every active target has the matching `actual`
- [ ] If you added a dependency, it lives in `gradle/libs.versions.toml`
- [ ] User-visible strings go through `stringResource(...)`
- [ ] No hardcoded colors — uses `MaterialTheme.colorScheme.*`
- [ ] No `local.properties` / `*.iml` / `xcuserdata/` / `.env` staged
- [ ] If you added a new realtime feed: subscription has a matching `unsubscribe()` and a `meetup_id` filter
- [ ] If you added a new SQL table: it's added to `supabase_realtime` publication in the migration
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

When a command is invoked, the agent MUST:

1. **Look up** the command in **[.claude/README.md](.claude/README.md)** to find its skill file
2. **READ** the linked skill file completely
3. **FOLLOW** its step-by-step instructions exactly
4. **DO NOT** improvise or skip steps — the skill file IS the spec

## Conventional Commits

**Format:** `<type>: <description>`

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`

Examples:
- `feat(meetups): create meetup with auto-generated join code`
- `feat(realtime): subscribe room participants to postgres-changes`
- `fix(supabase): unsubscribe channel in finally to avoid leaks`
- `chore(deps): bump supabase-kt to 3.7.0`
- `build(buildkonfig): drop exposeObjectWithName so Wasm compiles`
- `docs: document Supabase trust boundaries`
