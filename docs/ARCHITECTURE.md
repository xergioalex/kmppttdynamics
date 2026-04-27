# Architecture

This document explains the **big picture** of KMPStarter so a new contributor (human or agent) can be productive quickly. For day-to-day commands see [Development Commands](DEVELOPMENT_COMMANDS.md). For language-specific rules see [Standards](STANDARDS.md).

## High-level model

```
                          ┌────────────────────────────┐
                          │  composeApp/commonMain     │
                          │  ────────────────────────  │
                          │  • App.kt   (root @Comp.)  │
                          │  • Greeting.kt (logic)     │
                          │  • Platform.kt (expect)    │
                          │  • composeResources/       │
                          └────────────┬───────────────┘
                                       │ same Kotlin code
   ┌──────────────┬──────────────┬────┴────┬──────────────┬──────────────┐
   ▼              ▼              ▼         ▼              ▼              ▼
androidMain    iosMain        jvmMain   webMain        jsMain        wasmJsMain
  ↓              ↓              ↓         ↓              ↓              ↓
MainActivity   MainView-     application  Compose-     (only        (only
.setContent    Controller    { Window }   Viewport      actual       actual
  { App() }    { App() }       App()        App()        }              }
   ↓              ↓              ↓         ↓
Android APK    ComposeApp    Desktop      Web JS bundle
                .framework   installer    +
                (consumed    (Dmg/Msi/    Web Wasm bundle
                 by Xcode)    Deb)
```

Every platform mounts the **same** `App()` composable. Platform source sets only contain the entry-point glue and `actual` declarations.

## Project structure

```
KMPStarter/
├── AGENTS.md                          # Single source of truth for AI agents
├── CLAUDE.md → AGENTS.md              # Symlink (do not edit directly)
├── README.md                          # Human-facing intro
├── build.gradle.kts                   # Root build script (declares plugins, applies none)
├── settings.gradle.kts                # Module list + repositories + TYPESAFE_PROJECT_ACCESSORS
├── gradle.properties                  # JVM args, configuration cache, AndroidX flags
├── gradle/
│   ├── libs.versions.toml             # Single version catalog — pin all deps here
│   └── wrapper/                       # Gradle wrapper jar + properties
├── gradlew, gradlew.bat               # Wrapper launchers
├── local.properties                   # Android SDK path (gitignored)
│
├── composeApp/                        # The only Gradle subproject
│   ├── build.gradle.kts               # KMP target list, source-set deps, Android config
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/xergioalex/kmpstarter/
│       │   │   ├── App.kt             # @Composable App — all UI starts here
│       │   │   ├── Greeting.kt        # Pure-Kotlin shared logic (uses getPlatform())
│       │   │   └── Platform.kt        # interface Platform + expect fun getPlatform()
│       │   └── composeResources/
│       │       └── drawable/          # Shared images (compiled into Res object)
│       ├── commonTest/kotlin/         # Shared kotlin.test tests
│       │
│       ├── androidMain/
│       │   ├── kotlin/com/xergioalex/kmpstarter/
│       │   │   ├── MainActivity.kt    # ComponentActivity → setContent { App() }
│       │   │   └── Platform.android.kt   # actual fun getPlatform()
│       │   ├── AndroidManifest.xml    # Single MainActivity, MAIN/LAUNCHER intent filter
│       │   └── res/                   # Android-only resources (icons, strings)
│       │
│       ├── iosMain/kotlin/com/xergioalex/kmpstarter/
│       │   ├── MainViewController.kt  # fun MainViewController() = ComposeUIViewController { App() }
│       │   └── Platform.ios.kt        # actual fun getPlatform()
│       │
│       ├── jvmMain/kotlin/com/xergioalex/kmpstarter/
│       │   ├── main.kt                # application { Window { App() } }
│       │   └── Platform.jvm.kt        # actual fun getPlatform()
│       │
│       ├── webMain/                   # SHARED web entry (used by both JS and Wasm)
│       │   ├── kotlin/com/xergioalex/kmpstarter/main.kt   # ComposeViewport { App() }
│       │   └── resources/index.html   # Web shell (loads composeApp.js)
│       │
│       ├── jsMain/kotlin/com/xergioalex/kmpstarter/
│       │   └── Platform.js.kt         # actual fun getPlatform()
│       │
│       └── wasmJsMain/kotlin/com/xergioalex/kmpstarter/
│           └── Platform.wasmJs.kt     # actual fun getPlatform()
│
├── iosApp/                            # Xcode project — consumes the ComposeApp framework
│   ├── iosApp.xcodeproj/
│   ├── iosApp/
│   │   ├── iOSApp.swift               # @main SwiftUI App
│   │   ├── ContentView.swift          # ComposeView wraps MainViewControllerKt.MainViewController()
│   │   ├── Info.plist
│   │   └── Assets.xcassets/
│   └── Configuration/                 # Build configurations
│
├── docs/                              # This documentation
└── tmp/                               # Git-ignored scratch space
```

## Source sets and the Kotlin Multiplatform hierarchy

Compose Multiplatform uses the default KMP source-set hierarchy. From most-shared to most-specific:

| Source set | Compiled for | Sees | Used for |
|---|---|---|---|
| `commonMain` | every active target | only multiplatform deps | All shared logic, UI, and `expect` declarations |
| `commonTest` | every test-capable target | `commonMain` + test deps | Shared tests |
| `webMain` | `jsMain` + `wasmJsMain` | `commonMain` + browser DOM | Web-only entry point shared by JS and Wasm |
| `androidMain` | Android target | Android SDK + `commonMain` | Android `actual`s, `MainActivity`, manifest |
| `iosMain` | `iosArm64` + `iosSimulatorArm64` | `commonMain` + Apple Foundation/UIKit | iOS `actual`s, `MainViewController` factory |
| `jvmMain` | Desktop JVM target | Java SE + `commonMain` | Desktop entry point and `actual`s |
| `jsMain` | JS target only | `commonMain` + Kotlin/JS DOM | JS-only `actual`s |
| `wasmJsMain` | Wasm target only | `commonMain` + Kotlin/Wasm DOM | Wasm-only `actual`s |

`webMain` is the **shared web** source set: both `jsMain` and `wasmJsMain` automatically depend on it. That's why the entry point (`ComposeViewport { App() }`) lives once in `webMain` and not twice.

## Platform abstraction

The canonical pattern is the contract in `commonMain` plus an `actual` per target:

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

```kotlin
// iosMain/Platform.ios.kt
import platform.UIKit.UIDevice
class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}
actual fun getPlatform(): Platform = IOSPlatform()
```

**When to add a new `expect`:**

- A piece of code in `commonMain` needs a value or behavior that only the platform can provide (clock, file system, secure storage, push token, network engine, etc.)
- The cross-platform abstraction is small (one or two functions / a tight interface)

**When NOT to:**

- The behavior is identical on every platform — just write it in `commonMain`
- The abstraction would balloon — prefer composing from a multiplatform library (Ktor, Kotlinx coroutines, kotlinx-datetime) that already provides cross-platform actuals
- The need is one-off in a single platform's entry point — keep it in that platform source set

## Compose Multiplatform integration

- `commonMain` declares `compose-runtime`, `compose-foundation`, `compose-material3`, `compose-ui`, `compose-components-resources`, and the AndroidX lifecycle Compose libraries — so the same composables compile for every target
- `compose.desktop.currentOs` is added in `jvmMain` for the desktop window APIs
- The `compose-uiTooling` debug dependency is Android-only (Layout Inspector / preview)
- The Compose Compiler plugin is applied via `libs.plugins.composeCompiler` (matched to the Kotlin version)

### Resources

Compose Multiplatform compiles `composeApp/src/commonMain/composeResources/` into a generated `kmpstarter.composeapp.generated.resources.Res` object. Subfolder conventions:

- `drawable/` — vector and raster images, accessed via `Res.drawable.<name>`
- `values/strings.xml` — base locale strings, accessed via `Res.string.<name>` (qualifiers like `values-es/` add localized variants)
- `font/` — bundled fonts
- `files/` — arbitrary files (read with `Res.readBytes("files/foo.json")`)

Generated identifiers strip the file extension. For details see [I18N Guide](I18N_GUIDE.md).

### iOS framework bridge

The Kotlin/Native iOS targets emit a static framework named `ComposeApp`. The Xcode project links against it and the Swift side does:

```swift
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
}
```

`MainViewControllerKt` is the auto-generated Objective-C class corresponding to `MainViewController.kt` (Kotlin top-level functions in a file `Foo.kt` are exposed under `FooKt`). If you rename the file or the function, update the Swift call site.

### Web entry split

`webMain` holds the actual `main()` and `index.html`. `jsMain` and `wasmJsMain` only contain a one-liner `Platform.<target>.kt`. This avoids duplicating the bootstrap when adding a third web variant in the future.

## Build configuration highlights

`composeApp/build.gradle.kts`:

- Targets enabled: `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`, `jvm()`, `js { browser() }`, `wasmJs { browser() }` — all with executables
- iOS framework name `ComposeApp`, `isStatic = true`
- Android: `compileSdk 36`, `minSdk 24`, `targetSdk 36`, Java 11; release build `isMinifyEnabled = false` (flip on for production — see [Performance](PERFORMANCE.md))
- Desktop: target formats `Dmg`, `Msi`, `Deb`; main class `com.xergioalex.kmpstarter.MainKt`

`gradle.properties`:

- `kotlin.code.style=official`
- `org.gradle.configuration-cache=true` and `org.gradle.caching=true` — fast builds, but be careful not to capture environment-dependent values in build logic
- `android.nonTransitiveRClass=true`, `android.useAndroidX=true`

`settings.gradle.kts`:

- `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` — reference modules as `projects.composeApp` instead of `project(":composeApp")`
- Foojay toolchain resolver for automatic JDK provisioning

## Mental model summary

1. **Shared by default.** New code goes in `commonMain`; you only descend into a platform source set when forced.
2. **One UI tree.** The same `App()` composable runs on all five targets — there is no platform-specific UI graph.
3. **Single version catalog.** All dependency versions live in `gradle/libs.versions.toml`; never inline a literal version string.
4. **`expect`/`actual` is the only platform escape hatch.** If the abstraction is large or one-off, find a multiplatform library or keep it inside the platform's entry point.
5. **The iOS framework name is load-bearing.** `ComposeApp` is referenced from Swift code in `iosApp/`; renaming requires updating both sides.
