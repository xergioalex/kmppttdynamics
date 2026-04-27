# Platforms

Per-target reference: entry point, capabilities, gotchas. Read the section for the platform you're touching before writing platform-specific code.

## Summary

| Target | Source set | Entry point | Output | Min version |
|---|---|---|---|---|
| Android | `androidMain` | `MainActivity.setContent { App() }` | APK / AAB | API 24 (Android 7.0) |
| iOS | `iosMain` | `MainViewController()` (called from Swift) | Static framework `ComposeApp` | iOS 13 (Compose-MP minimum) |
| Desktop JVM | `jvmMain` | `application { Window { App() } }` | Dmg / Msi / Deb | Java 11 |
| Web Wasm | `webMain` (shared) | `ComposeViewport { App() }` | Static bundle | Modern browsers (Wasm GC) |
| Web JS | `webMain` (shared) | `ComposeViewport { App() }` | Static bundle | Older browsers (legacy) |

---

## Android

### Entry point

`composeApp/src/androidMain/kotlin/com/xergioalex/kmptodoapp/MainActivity.kt`:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
```

### Manifest

`composeApp/src/androidMain/AndroidManifest.xml` declares:

- Application label `@string/app_name` (defined in `androidMain/res/values/strings.xml`)
- `MainActivity` with `android.intent.action.MAIN` / `android.intent.category.LAUNCHER`
- Theme: `@android:style/Theme.Material.Light.NoActionBar` (Compose draws its own UI; the system theme just sets initial window background)
- `android:supportsRtl="true"` — keep this on

### Build configuration

In `composeApp/build.gradle.kts`:

- `applicationId` and `namespace`: `com.xergioalex.kmptodoapp`
- `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`
- `versionCode = 1`, `versionName = "1.0"`
- `release { isMinifyEnabled = false }` — **change for production** (see [Performance](PERFORMANCE.md))
- Java 11 source/target

### Resources

Two pools:

- **Compose Multiplatform resources** in `commonMain/composeResources/` — preferred for anything shared
- **Android-only resources** in `androidMain/res/` — launcher icons (`mipmap-*`), the manifest label string, themes

### Capabilities

- All Android SDK APIs are reachable from `androidMain`
- For `Context`-dependent things in shared code, expose them via an `expect` and provide the `Context` from `MainActivity`
- ViewModels: use `androidx.lifecycle:lifecycle-viewmodel-compose` (already wired in `commonMain`) — `viewModel()` works the same as on iOS/Desktop/Web

### Gotchas

- `enableEdgeToEdge()` opts into drawing under system bars. Use `WindowInsets` modifiers (`safeContentPadding()` is used in `App.kt`) to avoid clipping content under the status bar / nav bar
- The system back button on Android invokes the default `onBackPressed()` — for in-app navigation you need an explicit back handler (`BackHandler` from `androidx.activity.compose`)
- `MainActivity` is a **single-activity** app; new screens are composables, not new activities

---

## iOS

### Entry point

The Kotlin side exposes `MainViewController()`:

```kotlin
// iosMain/MainViewController.kt
fun MainViewController() = ComposeUIViewController { App() }
```

The Swift side consumes it:

```swift
// iosApp/iosApp/ContentView.swift
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View { ComposeView().ignoresSafeArea() }
}
```

```swift
// iosApp/iosApp/iOSApp.swift
@main
struct iOSApp: App {
    var body: some Scene { WindowGroup { ContentView() } }
}
```

### Framework

- Name: **`ComposeApp`** — load-bearing, hardcoded in Swift `import ComposeApp`
- Type: static (`isStatic = true` in `composeApp/build.gradle.kts`)
- Targets: `iosArm64` (devices) and `iosSimulatorArm64` (Apple Silicon simulators only)
- To support Intel simulators (rare today) add `iosX64()`

### Building

- Open `iosApp/iosApp.xcodeproj` in Xcode and ⌘R, or use the IDE's KMP run config
- Command-line link tasks: `:composeApp:linkDebugFrameworkIosSimulatorArm64`, `:composeApp:linkReleaseFrameworkIosArm64`
- The Xcode project's "Run Script" build phase invokes Gradle to produce the framework — don't disable it

### Info.plist

`iosApp/iosApp/Info.plist` enables `CADisableMinimumFrameDurationOnPhone` for 120Hz support on iPhone Pro models. Bundle id, display name, version, and build number live in the Xcode project (`Configuration/`).

### Capabilities

- Full UIKit + Foundation reachable from `iosMain`
- Use `@OptIn(ExperimentalForeignApi::class)` for low-level interop
- `kotlinx-datetime`, `Ktor`, and the AndroidX Lifecycle multiplatform libraries all support iOS — prefer these over hand-rolled `expect`s

### Gotchas

- Kotlin top-level functions in file `Foo.kt` are exposed to ObjC/Swift as `FooKt.<name>` — renaming files breaks Swift call sites
- Static frameworks can collide with other static frameworks containing the same symbols. Prefer using only one Compose-MP framework per app
- The simulator target is **arm64** only — running on an Intel Mac requires adding `iosX64()`
- Compose-MP iOS framework startup is sensitive to whether the framework is static. Don't switch to dynamic without measuring

---

## Desktop (JVM)

### Entry point

```kotlin
// jvmMain/main.kt
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "KMPTodoApp") {
        App()
    }
}
```

### Build configuration

In `composeApp/build.gradle.kts`:

```kotlin
compose.desktop {
    application {
        mainClass = "com.xergioalex.kmptodoapp.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.xergioalex.kmptodoapp"
            packageVersion = "1.0.0"
        }
    }
}
```

### Capabilities

- Full Java SE
- Compose Hot Reload is enabled — `:composeApp:run` reloads composables without restart
- Add JVM dependencies via `jvmMain.dependencies { implementation(...) }`
- `Dispatchers.Main` is provided by `kotlinx-coroutines-swing` (already wired)

### Distribution

```bash
./gradlew :composeApp:packageDistributionForCurrentOS         # Debug installer
./gradlew :composeApp:packageReleaseDistributionForCurrentOS  # Optimized
```

Outputs land in `composeApp/build/compose/binaries/`. macOS produces `.dmg`, Windows `.msi`, Linux `.deb`. To produce installers for other OSes, run on that OS (cross-OS packaging is not supported by jpackage).

### Gotchas

- Desktop uses **Skia** (not AWT) for drawing; AWT components don't compose with Compose-MP nicely. If you embed AWT, expect rendering quirks
- Configuration cache + Compose Hot Reload have occasionally interacted oddly. If hot reload misbehaves, run `./gradlew --stop` and try again
- The macOS `.dmg` is unsigned by default — you'll need an Apple Developer ID for distribution outside dev machines

---

## Web (Wasm)

### Entry point

Both web targets share `webMain`:

```kotlin
// webMain/main.kt
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { App() }
}
```

`webMain/resources/index.html` is the host page. It loads `composeApp.js` (the Wasm glue is wrapped in a JS shim).

### Building

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun     # http://localhost:8080
./gradlew :composeApp:wasmJsBrowserDistribution       # Production bundle
```

Output: `composeApp/build/dist/wasmJs/productionExecutable/`. Deploy this directory as a static site (Cloudflare Pages, Netlify, Vercel, S3 + CloudFront, GitHub Pages with the right base href).

### Capabilities

- Full Compose Multiplatform UI runs in the browser
- DOM access via `kotlinx.browser.window`, `document`
- Wasm GC is required — needs Chrome 119+, Firefox 120+, Safari 18.2+
- Bundle is significantly smaller and faster than the JS target

### Gotchas

- Some libraries are JS-only or JVM-only — verify the dependency has a `wasmJs` artifact before adding it
- File downloads / clipboard / window APIs go through `kotlinx-browser` shims
- Set the `base href` correctly in `index.html` if hosting under a subpath
- Wasm errors print stack traces with mangled names — keep source maps enabled in dev

---

## Web (JS — fallback)

Same entry point and `index.html` as Wasm (shared `webMain` source set). `jsMain/Platform.js.kt` provides the JS-specific `actual fun getPlatform()`.

### Building

```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
./gradlew :composeApp:jsBrowserDistribution
```

### When to ship JS

Only when you need to support browsers without Wasm GC (older Safari, IE-era enterprise environments). The JS target is **slower** and **larger** than Wasm. Prefer Wasm and treat JS as a compatibility shim.

### Gotchas

- Same library compatibility caveat as Wasm — verify each dependency publishes a `js` artifact
- `console.log` is your friend; Compose-MP for JS doesn't have a fancy debugger
- The JS target's bundling pipeline is webpack-based; advanced customization goes in `composeApp/karma.config.d/` and `composeApp/webpack.config.d/`

---

## Choosing a target to add

If you discover you need a new target:

| Need | Add |
|---|---|
| Intel-Mac simulator support | `iosX64()` |
| Real-Mac native (not Catalyst) | `macosArm64()` + `macosX64()` |
| Apple TV | `tvosArm64()` + `tvosSimulatorArm64()` |
| watchOS | `watchosArm64()` + simulator variants |
| Linux native (CLI/embedded) | `linuxX64()` (Compose-MP support varies — check first) |

For each new target you'll need to:

1. Add it in `composeApp/build.gradle.kts`
2. Create the source set folder
3. Provide `actual`s for every existing `expect`
4. Document it here and in [Technologies](TECHNOLOGIES.md)
