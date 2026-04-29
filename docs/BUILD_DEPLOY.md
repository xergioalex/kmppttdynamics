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
-keep class com.xergioalex.kmppttdynamics.** { *; }
-dontwarn org.jetbrains.compose.**

# Add app-specific keep rules as you adopt libraries (Ktor, kotlinx-serialization, etc.)
```

3. **Configure signing.** Create a release keystore (one-time):

```bash
keytool -genkey -v -keystore release.keystore -alias kmppttdynamics -keyalg RSA -keysize 2048 -validity 10000
```

Store the keystore **outside the repo** and reference it via environment variables or `~/.gradle/gradle.properties`:

```properties
# ~/.gradle/gradle.properties (NOT checked in)
KMPPTTDYNAMICS_KEYSTORE_FILE=/Users/you/keys/kmppttdynamics-release.keystore
KMPPTTDYNAMICS_KEYSTORE_PASSWORD=...
KMPPTTDYNAMICS_KEY_ALIAS=kmppttdynamics
KMPPTTDYNAMICS_KEY_PASSWORD=...
```

```kotlin
// composeApp/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("KMPPTTDYNAMICS_KEYSTORE_FILE").get())
            storePassword = providers.gradleProperty("KMPPTTDYNAMICS_KEYSTORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("KMPPTTDYNAMICS_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("KMPPTTDYNAMICS_KEY_PASSWORD").get()
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
# Output (macOS): composeApp/build/compose/binaries/main/dmg/com.xergioalex.kmppttdynamics-1.0.0.dmg
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

The web target compiles to a fully-static bundle: HTML + CSS + JS + Wasm + bundled resources, no server runtime required. Deploy to any static host (Cloudflare Pages, Netlify, Vercel, GitHub Pages, S3 + CloudFront, plain nginx).

### Wasm production bundle

```bash
./scripts/build_web.sh
# or, equivalently
./gradlew :composeApp:wasmJsBrowserDistribution
# Output: composeApp/build/dist/wasmJs/productionExecutable/
```

`build_web.sh` is the recommended entry point: it cleans the previous output (so stale hashed chunks don't sneak in), runs the Gradle task, prints the contents and total size, and surfaces the Cloudflare Pages config you need on the dashboard.

Bundle composition (uncompressed; Brotli/gzip cuts ~3×):

| File | Size | Notes |
|---|---|---|
| `<hash>.wasm` (skiko) | ~8 MB | Compose Multiplatform's Skia port. Same for every Compose Wasm app |
| `<hash>.wasm` (composeApp) | ~5–6 MB | Our app code |
| `composeApp.js` | ~590 KB | Webpack entry point — non-hashed, short cache |
| `composeApp.js.map` | ~1.5 MB | Source map; useful for debugging prod errors |
| `composeResources/` | ~3.5 MB | 132 avatar PNGs + strings + fonts |
| `index.html`, `styles.css`, `_headers`, `_redirects` | small | Branded boot screen + CDN config |

Total uncompressed: ~20 MB. Brotli'd-on-the-wire: ~5–7 MB.

### Cloudflare Pages — dashboard setup

Recommended deploy path. Pages auto-builds on every push to the configured branch.

| Field | Value |
|---|---|
| Production branch | `main` |
| Build command | `bash scripts/build_web.sh` |
| Build output directory | `composeApp/build/dist/wasmJs/productionExecutable` |
| Root directory | (leave empty — repo root) |
| Environment variables | `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `JAVA_VERSION=21` |

**Env vars** — both Supabase values are public keys (RLS gates the data), so they're safe to bake in. The `JAVA_VERSION=21` is a Cloudflare Pages convention that triggers their build image to provision the requested JDK; without it the build picks the default Java 17 which Gradle 8.14 + Kotlin 2.3.20 don't fully support.

**Headers and redirects** — `composeApp/src/webMain/resources/_headers` and `_redirects` are copied into the output dir on every build:

- `_headers` sets `Cache-Control: public, max-age=31536000, immutable` on every content-hashed `.wasm` / chunk / `.map`, `must-revalidate` on the non-hashed entry points (`composeApp.js`, `index.html`). Cloudflare picks them up automatically per [their headers docs](https://developers.cloudflare.com/pages/configuration/headers/).
- `_redirects` has a single SPA fallback (`/* /index.html 200`) so any unknown path lands on the in-app router. The status `200` (not `301`) keeps the URL untouched so future deep-link handling can still read `window.location` from Kotlin.

**MIME types** — Cloudflare serves `.wasm` as `application/wasm` automatically. No tweak needed.

**Compression** — Cloudflare auto-applies Brotli to compressible MIME types; `.wasm` and `.js` both qualify.

**Base href** — only relevant if you ever host under a subpath (e.g., `https://example.com/app/`). Edit `webMain/resources/index.html` and add `<base href="/app/">` inside `<head>`.

### Cloudflare Pages — manual deploy via wrangler

Useful for one-off deploys or CI providers where you can't connect Pages to a repo.

```bash
npm i -g wrangler
bash scripts/build_web.sh
wrangler pages deploy composeApp/build/dist/wasmJs/productionExecutable \
    --project-name=ptt-dynamics \
    --branch=main
```

Wrangler reads `CLOUDFLARE_ACCOUNT_ID` + `CLOUDFLARE_API_TOKEN` from env or `~/.wrangler` for auth.

### Other hosts

The bundle is plain static files — works as-is on any host. Notes:

| Host | Notes |
|---|---|
| Netlify | `_headers` and `_redirects` use the same Netlify-compatible format. No changes needed |
| Vercel | Use `vercel.json` instead — Vercel ignores `_headers` / `_redirects`. Convert the cache rules into `headers` and the SPA fallback into a `rewrites` entry |
| GitHub Pages | No support for cache headers — just upload the dir, GitHub serves with default headers |
| S3 + CloudFront | Set the Wasm MIME type explicitly via S3 `Content-Type` metadata; configure CloudFront cache behaviors mirroring the rules in `_headers` |
| Plain nginx | Add `application/wasm` to `mime.types` if missing; serve with `expires 1y` for hashed assets and `expires 0` for `index.html` |

### JS production bundle (fallback only)

```bash
./gradlew :composeApp:jsBrowserDistribution
# Output: composeApp/build/dist/js/productionExecutable/
```

JS bundle is larger and slower than Wasm at runtime — use only for browsers / WebViews without Wasm support (very rare in practice). The two targets share the entry point in `webMain/`, so both produce the same UI from the same Kotlin source.

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
| `web-prod` | Linux | `bash scripts/build_web.sh`, then deploy via wrangler / your CDN of choice |

Add CI configs once you pick a provider. Document which providers you settled on in [Technologies](TECHNOLOGIES.md).

## Deployment checklist

- [ ] Version bumped (`versionCode`/`versionName` for Android, build number for iOS, `packageVersion` for Desktop)
- [ ] Release notes drafted
- [ ] Android: R8 enabled, ProGuard rules cover all reflection-using libraries, signing config in place
- [ ] iOS: signing & capabilities set, build archived against an Apple Silicon Mac
- [ ] Desktop: signed and notarized for macOS/Windows
- [ ] Web: bundle deployed with correct caching headers and MIME types
- [ ] Smoke test the artifact on a real device/browser before announcing
