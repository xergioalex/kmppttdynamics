# Development Commands

Reference for every Gradle task you'll run during day-to-day development. The Gradle wrapper (`./gradlew`) is the canonical entry point — never call a system-installed `gradle`. Run from the repo root.

> **Java 21.** Build with JDK 21. Gradle 8.14 doesn't recognise newer JDKs.
> ```bash
> export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
> ```
> When you run from Android Studio's Run config, the IDE supplies its own JDK and you don't need this export.

## Inner-loop favorites

| Goal | Command | Notes |
|---|---|---|
| Iterate on UI | `./gradlew :composeApp:run` | Desktop window with Compose Hot Reload — fastest UI loop |
| Iterate on logic | `./gradlew :composeApp:jvmTest` | JVM tests run in seconds |
| Run on Android | `./gradlew :composeApp:installDebug` then launch the app from the device | Requires a running emulator or connected device |
| Run on iOS | Open `iosApp/iosApp.xcodeproj` in Xcode and ⌘R | Or use the IDE's KMP run config |
| Run in browser (Wasm) | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` | Auto-opens `http://localhost:8080` |
| Type-check everything | `./gradlew :composeApp:assemble` | Compiles all targets without packaging extras |
| Apply DB migrations | `./scripts/supabase_apply.sh` | Reads `.env` for `SUPABASE_DB_URL` (or builds it from `SUPABASE_PROJECT_REF` + `SUPABASE_DB_PASSWORD`); runs every file in `supabase/migrations/` via `psql` |

## Running the app

### Desktop (JVM)

```bash
./gradlew :composeApp:run                              # With hot reload (default — plugin is applied)
./gradlew :composeApp:packageDistributionForCurrentOS  # Build a native installer (Dmg on macOS, Msi on Windows, Deb on Linux)
./gradlew :composeApp:packageReleaseDistributionForCurrentOS  # Same, optimized
```

The desktop main class is `com.xergioalex.kmppttdynamics.MainKt` — set in `composeApp/build.gradle.kts`.

### Android

```bash
./gradlew :composeApp:assembleDebug         # Build debug APK
./gradlew :composeApp:installDebug          # Build + install on device/emulator
./gradlew :composeApp:assembleRelease       # Build release APK (currently unsigned, no R8)
./gradlew :composeApp:bundleRelease         # Build release AAB for Play Store
./gradlew :composeApp:lint                  # Android lint (when configured)
adb logcat | grep KMPPTTDynamics                  # Tail logs
```

The debug APK lands at `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

### iOS

iOS builds are driven by Xcode. The Gradle plugin emits a static framework that the Xcode project links against.

```bash
# From command line (rare — open Xcode instead):
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :composeApp:linkReleaseFrameworkIosArm64
```

To run:

1. Open `iosApp/iosApp.xcodeproj` in Xcode
2. Select a simulator or device
3. ⌘R

Or use the **IDE's KMP run config** (Android Studio with the KMP plugin, or Fleet) — it runs the Gradle link task and then `xcodebuild` for you.

### Web (Wasm — preferred)

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun       # Dev server with HMR
./gradlew :composeApp:wasmJsBrowserDistribution         # Production bundle to composeApp/build/dist/wasmJs/productionExecutable
```

### Web (JS — fallback for older browsers)

```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
./gradlew :composeApp:jsBrowserDistribution
```

## Testing

```bash
./gradlew :composeApp:allTests                          # All targets that support tests
./gradlew :composeApp:jvmTest                           # JVM only — fastest
./gradlew :composeApp:testDebugUnitTest                 # Android unit tests (Robolectric-style, not instrumented)
./gradlew :composeApp:iosSimulatorArm64Test             # iOS simulator tests
./gradlew :composeApp:jsBrowserTest                     # JS browser test runner
./gradlew :composeApp:wasmJsBrowserTest                 # Wasm browser test runner
```

### Single test

```bash
./gradlew :composeApp:jvmTest --tests "com.xergioalex.kmppttdynamics.ComposeAppCommonTest.joinCodeIsSixCharsAlphanumeric"
./gradlew :composeApp:jvmTest --tests "com.xergioalex.kmppttdynamics.ComposeAppCommonTest"   # All in class
./gradlew :composeApp:jvmTest --tests "*JoinCode*"                                     # Pattern match
```

The `--tests` filter uses the JUnit pattern syntax. It works on `jvmTest` and `testDebugUnitTest` reliably; for Native and Wasm test targets the pattern support is more limited — run the whole class.

### Continuous test mode

```bash
./gradlew :composeApp:jvmTest --continuous              # Re-runs on file change
```

## Build inspection

```bash
./gradlew :composeApp:dependencies                      # Full dependency tree
./gradlew :composeApp:dependencies --configuration commonMainImplementation
./gradlew :composeApp:tasks --all                       # Every task in the module
./gradlew projects                                      # List subprojects
./gradlew --scan                                        # Generate a build scan (uploads to scans.gradle.com)
```

## Maintenance

```bash
./gradlew clean                                         # Delete build/ outputs
./gradlew --stop                                        # Stop the Gradle daemon (use after a corrupt build state)
./gradlew :composeApp:assemble --rerun-tasks            # Disable cache for one run
./gradlew wrapper --gradle-version=<version>            # Bump the wrapper
```

## Useful flags

| Flag | What it does | When to use |
|---|---|---|
| `--info` | Verbose Gradle logs | Debugging configuration issues |
| `--debug` | Debug-level logs | Last resort — very chatty |
| `--stacktrace` | Print stack trace on failure | Standard for triaging build errors |
| `--offline` | Skip network for dependency resolution | After a sync, to confirm reproducibility |
| `--rerun-tasks` | Bypass build cache | Suspecting stale outputs |
| `-Dkotlin.daemon.jvmargs="-Xmx4g"` | Override Kotlin daemon heap | Out-of-memory on large builds |
| `--configuration-cache-problems=warn` | Don't fail on config-cache issues | Debugging a config-cache problem |

## Common workflows

### Add a dependency

1. Open `gradle/libs.versions.toml`
2. Add the version to `[versions]` and the library to `[libraries]`
3. Reference it in the right `*.dependencies { ... }` block in `composeApp/build.gradle.kts` via `libs.<accessor>`
4. Run `./gradlew :composeApp:assemble` to confirm it resolves

### Add a new target source set

1. Add the target in `composeApp/build.gradle.kts` (e.g., `iosX64()`)
2. Sync (the IDE creates the source set folder, or `mkdir composeApp/src/iosX64Main/kotlin`)
3. Add `actual` declarations for every existing `expect` — the build will tell you which are missing

### Bump Kotlin / Compose

1. Cross-check the [Compose Multiplatform compatibility table](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html)
2. Bump in `gradle/libs.versions.toml`: `kotlin`, `composeMultiplatform`, possibly `composeHotReload`
3. `./gradlew clean :composeApp:assemble :composeApp:allTests`
4. Smoke-test each platform's run task

### Reset a stuck build

```bash
./gradlew --stop
rm -rf .gradle composeApp/build build
./gradlew :composeApp:assemble
```

If the issue persists, also delete `~/.gradle/caches` (heavy — only as a last resort).

## CI considerations

This starter has no CI configured yet. When you add one, suggested matrix:

- **Linux runner**: `:composeApp:assemble`, `:composeApp:allTests` minus iOS
- **macOS runner**: `:composeApp:iosSimulatorArm64Test`, framework link tasks, optional `xcodebuild`
- **Web bundle**: `:composeApp:wasmJsBrowserDistribution` and upload as artifact

Document the CI in this file once it exists.
