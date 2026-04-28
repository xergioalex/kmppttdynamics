# Technologies

A complete inventory of every tool, plugin, and library used by KMPPTTDynamics, with **versions, role, and where it's wired**. Every version is pinned in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) — change versions there, not in `build.gradle.kts`.

## Languages and runtimes

| Tool | Version | Role |
|---|---|---|
| Kotlin | **2.3.20** | Multiplatform language; compiles to JVM bytecode (Android, Desktop), Apple frameworks (iOS), JS, and Wasm |
| Java | **11** (source/target) | JVM bytecode level for Android and Desktop |
| Gradle Wrapper | shipped (`./gradlew`) | Builds the project — pinned per-repo so you don't depend on a system Gradle install |
| Android NDK / SDK | compileSdk **36**, minSdk **24**, targetSdk **36** | Android platform target |
| iOS | `iosArm64` + `iosSimulatorArm64` | Real devices and Apple Silicon simulators |
| Web | `js { browser() }` + `wasmJs { browser() }` | Browser execution targets |

## Gradle plugins

All plugins are declared in `gradle/libs.versions.toml` and applied per module via `alias(libs.plugins.*)`.

| Plugin | Version | Purpose |
|---|---|---|
| `org.jetbrains.kotlin.multiplatform` | 2.3.20 | KMP target configuration (`androidTarget`, `iosArm64`, `jvm`, `js`, `wasmJs`) |
| `com.android.application` | 8.11.2 | Build the Android APK from `composeApp` |
| `com.android.library` | 8.11.2 | (Declared, not yet applied — useful when extracting submodules) |
| `org.jetbrains.compose` | 1.10.3 | Compose Multiplatform — runtime, foundation, material3, ui, resources |
| `org.jetbrains.kotlin.plugin.compose` | 2.3.20 | Compose Compiler plugin — version-matched to Kotlin |
| `org.jetbrains.kotlin.plugin.serialization` | 2.3.20 | Generates serializers for `@Serializable` classes (used by the Postgrest payloads) |
| `com.codingfeline.buildkonfig` | 0.17.1 | Generates a multiplatform `BuildConfig` Kotlin object from `.env` values, available in `commonMain` |
| `org.jetbrains.compose.hot-reload` | 1.0.0 | Hot reload of composables on Desktop JVM |
| `org.gradle.toolchains.foojay-resolver-convention` | 1.0.0 | Auto-provisions a matching JDK for the toolchain |

## Compose Multiplatform stack

Wired in `commonMain.dependencies { ... }` (see `composeApp/build.gradle.kts`).

| Library | Version (`composeMultiplatform`) | Role |
|---|---|---|
| `compose-runtime` | 1.10.3 | Composition runtime, `@Composable`, state, recomposer |
| `compose-foundation` | 1.10.3 | Layout (`Row`, `Column`, `LazyColumn`), gesture, scroll |
| `compose-material3` | **1.10.0-alpha05** | Material 3 components (`Button`, `MaterialTheme`, `Scaffold`) — alpha because Material3 lags behind core Compose |
| `compose-ui` | 1.10.3 | Modifiers, drawing, semantics, low-level UI primitives |
| `compose-components-resources` | 1.10.3 | Generated `Res` for `composeResources/` |
| `compose-uiToolingPreview` | 1.10.3 | `@Preview` annotation surface — multiplatform safe |
| `compose-uiTooling` (debug, Android-only) | 1.10.3 | Layout inspector / preview rendering inside Android Studio |

> Material 3 is on an `alpha` channel because the multiplatform port follows the AndroidX Material 3 release cadence with a slight delay. Treat alpha versions like any other dependency — pin and review release notes before bumping.

## AndroidX Lifecycle (multiplatform)

| Library | Version (`androidx-lifecycle`) | Role |
|---|---|---|
| `androidx-lifecycle-viewmodel-compose` | 2.10.0 | `ViewModel` + `viewModel()` available across all KMP targets |
| `androidx-lifecycle-runtime-compose` | 2.10.0 | `collectAsStateWithLifecycle()` and lifecycle-aware Compose helpers |

These are the **multiplatform** ports published by JetBrains under `org.jetbrains.androidx.lifecycle` — not the Android-only AndroidX artifacts. The same `ViewModel` works on iOS, Desktop, and Web.

## Coroutines

| Library | Version | Where | Role |
|---|---|---|---|
| `kotlinx-coroutines-core` | 1.10.2 | `commonMain` | Structured concurrency — `Flow`, `viewModelScope.launch`, `NonCancellable` for safe channel cleanup |
| `kotlinx-coroutines-swing` | 1.10.2 | `jvmMain` | Provides `Dispatchers.Main` backed by Swing's EDT for the Desktop window |

## Supabase + Ktor

| Library | Version | Where | Role |
|---|---|---|---|
| `io.github.jan-tennert.supabase:postgrest-kt` | 3.6.0 | `commonMain` | Type-safe REST client for Supabase Postgres |
| `io.github.jan-tennert.supabase:realtime-kt` | 3.6.0 | `commonMain` | Realtime channels (`postgresChangeFlow` + Presence) |
| `io.ktor:ktor-client-core` | **3.2.3** | `commonMain` | HTTP transport interface used by supabase-kt |
| `io.ktor:ktor-client-cio` | 3.2.3 | `androidMain` + `jvmMain` | CIO engine for Android / Desktop |
| `io.ktor:ktor-client-darwin` | 3.2.3 | `iosMain` | Native iOS engine |
| `io.ktor:ktor-client-js` | 3.2.3 | `jsMain` + `wasmJsMain` | Browser engine for both web targets |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | `commonMain` | JSON serialization for `@Serializable` domain models |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 0.7.1 | `commonMain` | Pairs with `kotlin.time.Instant` for typed timestamps from Postgres `timestamptz` |

The Supabase client is created lazily by `SupabaseClientProvider` from `BuildConfig.SUPABASE_URL` + `BuildConfig.SUPABASE_PUBLISHABLE_KEY`, so the dependency tree initialises only when the first repository call needs it. The `KotlinXSerializer` is configured with `Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false }` — defaultable fields on insert payloads must also be tagged with `@EncodeDefault(EncodeDefault.Mode.ALWAYS)` for belt-and-suspenders, see [Standards → Serialization](STANDARDS.md#serialization).

> ⚠ **Ktor must stay on the same minor as supabase-kt's BOM.** supabase-kt 3.6.0 ships with internal references to Ktor 3.2.x APIs (`dropCompressionHeaders` on the Darwin engine). Pinning Ktor to an older minor (e.g. 3.0.3) makes the Kotlin/Native iOS framework crash at runtime with `IrLinkageError: Function 'dropCompressionHeaders' can not be called`. Always bump the two together.

## Multiplatform Settings

| Library | Version | Role |
|---|---|---|
| `com.russhwolf:multiplatform-settings` | 1.2.0 | Persistent key-value storage with per-platform actuals (SharedPreferences on Android, NSUserDefaults on iOS, java.util.prefs on JVM, localStorage on Web) |
| `com.russhwolf:multiplatform-settings-no-arg` | 1.2.0 | Convenience constructor for the default backing store on each platform |

`AppSettings` (in `commonMain/settings/`) wraps the library and exposes:

- `themeMode: StateFlow<ThemeMode>`
- `profile: StateFlow<LocalProfile?>` — drives the onboarding gate in `App.kt`
- `installClientId(): String` — install-stable random hex string used as the presence key, `app_users` PK, and `meetup_participants.client_id`
- `participantIdFor(meetupId)` / `setParticipantIdFor(meetupId, id)` — local cache of "the participant row this device owns in this meetup"

## Activity (Android-only)

| Library | Version | Role |
|---|---|---|
| `androidx-activity-compose` | 1.13.0 | `setContent { }`, `enableEdgeToEdge()`, integration with `ComponentActivity` |

## Testing

| Library | Version | Role |
|---|---|---|
| `kotlin-test` | matches Kotlin (2.3.20) | Multiplatform `@Test`, `assertEquals`, etc. — works in `commonTest` |
| `kotlin-test-junit` | matches Kotlin | JUnit 4 backend used implicitly on JVM/Android targets when running `commonTest` |
| `junit` | 4.13.2 | Underlying assertion runner on JVM targets (declared but not actively wired into a source set yet) |

> The test stack is intentionally small. When you add testing dependencies (Turbine, Truth, MockK, Compose UI test, Roborazzi, etc.) put them in the catalog and document them here.

## Tooling versions referenced (declared, not used)

These are present in `libs.versions.toml` for convenience when you start adding Android-specific test infrastructure. They are **not** wired into any source set yet:

- `androidx-core` 1.18.0 (`androidx-core-ktx`)
- `androidx-appcompat` 1.7.1
- `androidx-testExt` 1.3.0 (`androidx-testExt-junit`)
- `androidx-espresso` 3.7.0 (`androidx-espresso-core`)

## Build / Gradle settings

`gradle.properties`:

- `kotlin.code.style=official` — IDE-driven Kotlin formatting; **don't** check in code that violates this
- `kotlin.daemon.jvmargs=-Xmx3072M` — Kotlin compiler heap
- `org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8` — Gradle daemon heap
- `org.gradle.configuration-cache=true` — re-use the configured task graph between invocations (large speedup; be careful with build logic that captures environment variables)
- `org.gradle.caching=true` — task output caching
- `android.nonTransitiveRClass=true` — smaller R classes, faster Android builds
- `android.useAndroidX=true` — required for any modern Android dependency

`settings.gradle.kts`:

- `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` — refer to modules as `projects.composeApp`
- Foojay toolchain resolver for automatic JDK download

## Settings persistence

| Library | Version | Role |
|---|---|---|
| `com.russhwolf:multiplatform-settings` | 1.2.0 | Persistent key-value: theme, profile (display name + avatar), per-meetup participant cache, install client id. Backed by `SharedPreferences` (Android), `NSUserDefaults` (iOS), `java.util.prefs` (JVM), `localStorage` (Web) — wired in each platform's `main`. See [Identity & avatars](IDENTITY_AND_AVATARS.md). |

## Build-time configuration (BuildKonfig)

`BuildKonfig` generates a Kotlin object exposed in `commonMain` as `com.xergioalex.kmppttdynamics.config.BuildConfig` with two fields:

- `BuildConfig.SUPABASE_URL`
- `BuildConfig.SUPABASE_PUBLISHABLE_KEY`

Values come from the project's `.env` file (or matching environment variables at build time). The remaining `.env` keys (`SUPABASE_PROJECT_REF`, `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`, `SUPABASE_DB_URL`, `SUPABASE_SECRET_KEY`) are read by `scripts/supabase_apply.sh` only — they never enter `BuildConfig`.

Configuration lives at the bottom of `composeApp/build.gradle.kts`. `exposeObjectWithName` is intentionally NOT set: the `@JsExport` it generates is rejected by the Kotlin/Wasm compiler on standalone objects.

## What is **not** wired (yet)

- **DI framework**: no Koin / Kotlin-Inject. `AppContainer` does the wiring by hand.
- **Logging**: no Napier / Kermit. `println` works while bootstrapping.
- **Linting**: no ktlint / detekt. Add either to enforce style — see [Standards](STANDARDS.md).
- **CI**: no GitHub Actions / Bitrise / Codemagic configs.
- **Crash reporting**: no Sentry / Crashlytics.
- **Offline cache**: SQLDelight was used in a previous Todo iteration of this repo and was removed for M1 because Supabase is the only source of truth. An offline read-cache is an M6+ follow-up.

When you add any of the above, **document the choice** in this file and link to the relevant guide.

## Upgrading

1. Edit only `gradle/libs.versions.toml` — never inline a version string in `build.gradle.kts`
2. Bump one library at a time when possible; multi-bumps mask which dependency broke a build
3. Compose Multiplatform and the Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) are tied to Kotlin version — read the [Compose Multiplatform compatibility table](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html) before bumping Kotlin
4. After bumping, run `./gradlew :composeApp:assemble :composeApp:allTests` and smoke-test each platform's run task
5. Use the `/bump-deps` skill (see [.claude/README.md](../.claude/README.md)) for a structured workflow
