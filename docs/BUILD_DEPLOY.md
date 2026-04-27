# Build & Deploy

How to produce shippable artifacts for each platform. The starter is unsigned and unconfigured for stores — this guide walks you through the steps you take *after* forking, when you're ready to release.

## Inputs you need before any release

- **Versioning:** `versionCode` (Android), `versionName`, iOS `CFBundleShortVersionString` + `CFBundleVersion`, Compose Desktop `packageVersion`
- **App icons:** Android adaptive icons, iOS asset catalog, Desktop application icon (per OS)
- **App name:** Android `strings.xml`, iOS `CFBundleDisplayName`, Desktop `Window(title=...)`
- **Bundle / application identifier:** `applicationId`/`namespace` (Android), `PRODUCT_BUNDLE_IDENTIFIER` (iOS), `packageName` (Desktop)

The fork checklist for these is in [Fork Customization](FORK_CUSTOMIZATION.md).

## Android

### Debug build

```bash
./gradlew :composeApp:assembleDebug
# Output: composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Debug builds are signed automatically with the Android debug keystore.

### Release build

The starter ships with `release { isMinifyEnabled = false }` and **no signing config**. To ship to production:

1. **Enable R8 and shrink resources:**

```kotlin
// composeApp/build.gradle.kts
buildTypes {
    getByName("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

2. **Add `composeApp/proguard-rules.pro`:**

```proguard
# Keep Compose Multiplatform / Kotlin/Native interop happy
-keep class com.xergioalex.kmpstarter.** { *; }
-dontwarn org.jetbrains.compose.**

# Add app-specific keep rules as you adopt libraries (Ktor, kotlinx-serialization, etc.)
```

3. **Configure signing.** Create a release keystore (one-time):

```bash
keytool -genkey -v -keystore release.keystore -alias kmpstarter -keyalg RSA -keysize 2048 -validity 10000
```

Store the keystore **outside the repo** and reference it via environment variables or `~/.gradle/gradle.properties`:

```properties
# ~/.gradle/gradle.properties (NOT checked in)
KMPSTARTER_KEYSTORE_FILE=/Users/you/keys/kmpstarter-release.keystore
KMPSTARTER_KEYSTORE_PASSWORD=...
KMPSTARTER_KEY_ALIAS=kmpstarter
KMPSTARTER_KEY_PASSWORD=...
```

```kotlin
// composeApp/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("KMPSTARTER_KEYSTORE_FILE").get())
            storePassword = providers.gradleProperty("KMPSTARTER_KEYSTORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("KMPSTARTER_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("KMPSTARTER_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            // ... R8 config from above
        }
    }
}
```

4. **Build:**

```bash
./gradlew :composeApp:bundleRelease       # AAB for Play Store (preferred)
./gradlew :composeApp:assembleRelease     # APK for direct distribution
# Outputs:
#   composeApp/build/outputs/bundle/release/composeApp-release.aab
#   composeApp/build/outputs/apk/release/composeApp-release.apk
```

5. **Upload** the AAB to Play Console.

### Versioning Android releases

Bump in `composeApp/build.gradle.kts`:

- `versionCode` — integer that **must increase** with every Play upload
- `versionName` — semantic version string

Consider deriving these from a single source (a `version.properties` file) once you have a release cadence.

---

## iOS

iOS distribution goes through Xcode — the Gradle build only produces the framework.

### Debug

1. Open `iosApp/iosApp.xcodeproj`
2. Select a simulator or device, ⌘R

### Release / App Store

1. **Configure signing** in Xcode: select the project, target `iosApp`, "Signing & Capabilities" tab. Set Team and Bundle Identifier.
2. **Set version and build number** in target → "General":
   - Version (e.g., `1.0.0`)
   - Build (e.g., `1` — must increase per upload)
3. **Archive:** Product → Archive (use a real device or "Any iOS Device" as the destination)
4. **Distribute** from the Organizer window: App Store Connect → Upload, or Ad Hoc, or Development.

### TestFlight

After uploading via Archive → Distribute, the build appears in App Store Connect → TestFlight. Add testers and submit for Beta App Review (only the first build per version usually requires review).

### CI for iOS

Xcode Cloud, GitHub Actions on macOS runners, Bitrise, or Codemagic all work. Outline:

```bash
./gradlew :composeApp:linkReleaseFrameworkIosArm64
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -archivePath build/iosApp.xcarchive \
  archive
xcodebuild -exportArchive \
  -archivePath build/iosApp.xcarchive \
  -exportPath build/ipa \
  -exportOptionsPlist exportOptions.plist
```

`exportOptions.plist` controls the export (App Store / Ad Hoc / Development). Keep your provisioning profiles and certificates in the CI's secret store.

---

## Desktop (JVM)

### Build a native installer

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
# Output (macOS): composeApp/build/compose/binaries/main/dmg/com.xergioalex.kmpstarter-1.0.0.dmg
```

`packageReleaseDistributionForCurrentOS` is the optimized variant.

Cross-OS packaging is **not supported** — to produce a Windows `.msi` you need Windows, for `.dmg` macOS, for `.deb` Linux. CI matrix is the standard solution.

### macOS notarization

The default `.dmg` is unsigned. Distribution outside developer machines requires:

1. A Developer ID Application certificate
2. Sign the app bundle: `codesign --deep --force --sign "Developer ID Application: ..." MyApp.app`
3. Notarize: `xcrun notarytool submit ...`
4. Staple: `xcrun stapler staple MyApp.dmg`

Compose Desktop has helpers for this — see the [Compose Desktop signing docs](https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution/README.md).

### Windows code signing

Use `signtool.exe` with an EV code signing certificate. Configure once and add to your CI.

### Linux

`.deb` for Debian/Ubuntu, AppImage if you want broader distribution (not built-in to Compose Desktop — use `appimagetool` post-build).

### Versioning

Bump `packageVersion` in `compose.desktop.application.nativeDistributions { ... }`.

---

## Web

### Wasm production bundle

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
# Output: composeApp/build/dist/wasmJs/productionExecutable/
```

Static hosting works on Cloudflare Pages, Netlify, Vercel, GitHub Pages, S3 + CloudFront. Configuration:

- **Caching:** all assets except `index.html` are content-hashed — set far-future cache headers on JS / Wasm / CSS, no-cache on `index.html`
- **Compression:** make sure your CDN serves Brotli or gzip; Wasm files compress well
- **Base href:** if hosting under a subpath (e.g., `https://example.com/app/`), edit `webMain/resources/index.html` to set `<base href="/app/">`
- **MIME types:** `.wasm` must be `application/wasm`. Most CDNs handle this; some require a config tweak

### JS production bundle

```bash
./gradlew :composeApp:jsBrowserDistribution
# Output: composeApp/build/dist/js/productionExecutable/
```

Same hosting story. Bundle is larger and slower than Wasm — use only as a fallback.

### Versioning

There's no version stamp for the web target by default. Add one to your CI: write the git SHA into `index.html` or expose it in the bundle name.

---

## Cross-platform CI matrix

A reasonable starter pipeline:

| Job | Runner | Steps |
|---|---|---|
| `lint-and-test` | Linux | `./gradlew :composeApp:assemble :composeApp:allTests` (skip iOS) |
| `android-release` | Linux | Build AAB, upload to Play (alpha/internal) |
| `ios-release` | macOS | Link framework, `xcodebuild archive`, upload to TestFlight |
| `desktop-mac` | macOS | `packageReleaseDistributionForCurrentOS`, sign + notarize |
| `desktop-win` | Windows | `packageReleaseDistributionForCurrentOS`, sign |
| `desktop-linux` | Linux | `packageReleaseDistributionForCurrentOS` |
| `web-prod` | Linux | `wasmJsBrowserDistribution`, deploy to CDN |

Add CI configs once you pick a provider. Document which providers you settled on in [Technologies](TECHNOLOGIES.md).

## Deployment checklist

- [ ] Version bumped (`versionCode`/`versionName` for Android, build number for iOS, `packageVersion` for Desktop)
- [ ] Release notes drafted
- [ ] Android: R8 enabled, ProGuard rules cover all reflection-using libraries, signing config in place
- [ ] iOS: signing & capabilities set, build archived against an Apple Silicon Mac
- [ ] Desktop: signed and notarized for macOS/Windows
- [ ] Web: bundle deployed with correct caching headers and MIME types
- [ ] Smoke test the artifact on a real device/browser before announcing
