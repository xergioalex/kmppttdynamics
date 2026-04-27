# Technologies

A complete inventory of every tool, plugin, and library shipped with this starter, with **versions, role, and where it's wired**. Every version is pinned in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) — change versions there, not in `build.gradle.kts`.

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
| `kotlinx-coroutines-swing` | 1.10.2 | `jvmMain` | Provides `Dispatchers.Main` backed by Swing's EDT for the Desktop window |

`commonMain` doesn't declare a coroutines dependency directly — Compose pulls it in transitively. Add it explicitly to the catalog if you start using `kotlinx.coroutines` APIs from shared code.

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

## What this starter does **not** ship

Deliberately omitted so you can add the right tool for your project:

- **Networking**: no Ktor, OkHttp, or Retrofit. Add `io.ktor:ktor-client-*` to `commonMain` when needed.
- **Serialization**: no `kotlinx-serialization`. Add the plugin (`org.jetbrains.kotlin.plugin.serialization`) and the `kotlinx-serialization-json` dependency together.
- **Persistence**: no SQLDelight, Room KMP, or DataStore. Pick the one that matches your data shape.
- **DI**: no Koin or Kotlin-Inject. The starter is small enough to wire dependencies by hand.
- **Logging**: no Napier or Kermit. `println` works in all targets while you bootstrap.
- **Linting**: no ktlint or detekt. Add either to enforce style — see [Standards](STANDARDS.md).
- **CI**: no GitHub Actions / Bitrise / Codemagic configs. Add per your hosting choice.
- **Crash reporting**: no Sentry / Crashlytics.

When you add any of the above, **document the choice** in this file and link to the relevant guide.

## Upgrading

1. Edit only `gradle/libs.versions.toml` — never inline a version string in `build.gradle.kts`
2. Bump one library at a time when possible; multi-bumps mask which dependency broke a build
3. Compose Multiplatform and the Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) are tied to Kotlin version — read the [Compose Multiplatform compatibility table](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html) before bumping Kotlin
4. After bumping, run `./gradlew :composeApp:assemble :composeApp:allTests` and smoke-test each platform's run task
5. Use the `/bump-deps` skill (see [.claude/README.md](../.claude/README.md)) for a structured workflow
