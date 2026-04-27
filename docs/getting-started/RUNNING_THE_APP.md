# Running the App

How to run KMPStarter on every target. Each section explains what to do, why it works, and how to confirm the app actually launched.

If you haven't installed your tools yet, start with [Environment Setup](ENVIRONMENT_SETUP.md). For raw Gradle task references see [`../DEVELOPMENT_COMMANDS.md`](../DEVELOPMENT_COMMANDS.md). For per-platform internals see [`../PLATFORMS.md`](../PLATFORMS.md).

What the starter shows when it runs correctly: a button labeled **Click me!**, a Compose Multiplatform logo, and a greeting like:

```text
Compose: Hello, Android 36!     (or iOS / JVM / JS / Wasm)
```

That string is built from `commonMain` plus the target's `actual fun getPlatform()` — seeing the right platform name confirms the shared/native bridge works.

---

## 1. Android emulator

The fastest way to run on Android. The emulator runs locally and is great for inner-loop iteration.

### Steps

1. Open the project in Android Studio and wait for Gradle sync to finish.
2. Open **Device Manager** (`View → Tool Windows → Device Manager`).
3. Create a virtual device if you don't have one — e.g. **Medium Phone, API 36**.
4. In the run-config dropdown (top toolbar), select **composeApp**.
5. In the device dropdown, pick the emulator (e.g. *Medium Phone API 36.0*).
6. Click **Run** (`▶`) or press `^R`.

Or from the terminal — boot an emulator first, then:

```bash
./gradlew :composeApp:installDebug
adb shell monkey -p com.xergioalex.kmpstarter -c android.intent.category.LAUNCHER 1
```

### What you should see

The starter screen with `Compose: Hello, Android 36!` (or whatever API level the emulator is on).

### Verify

```bash
adb logcat | grep -i kmpstarter
```

Or just check the running window. If the build fails, see [Troubleshooting → Gradle / build issues](TROUBLESHOOTING.md#gradle-fails-with-what-went-wrong-26).

---

## 2. Physical Android device

Same flow as the emulator, just with a real phone. You only need to set this up once per device.

### One-time prep on the phone

See [Environment Setup → §8](ENVIRONMENT_SETUP.md#8-connect-a-physical-android-device-optional) — enable Developer Options, turn on USB debugging, and authorize your Mac.

### Verify the device is recognized

```bash
adb devices
```

You should see something like `samsung SM-S928B   device`. If it says `unauthorized`, accept the prompt on the phone again.

### Run

In Android Studio's device dropdown the phone now appears by model name. Select it and click **Run**.

### What you should see

The starter app installed and launched on your phone.

### Tips

- USB-C cable matters — some cables are charge-only and won't expose ADB. If `adb devices` is empty, try a different cable.
- Wireless ADB also works on Android 11+ (`Developer options → Wireless debugging`) once you've paired once over USB.

---

## 3. iOS simulator

The iOS path always involves Xcode somewhere because Xcode owns the simulator runtime, code signing, and the Swift wrapper app. The shared Kotlin code is built by Gradle and linked as the `ComposeApp` framework into the Xcode project.

> **Recommended path for first-timers**: build from Xcode directly. Once that works, you can also run from Android Studio (the IDE's KMP run config calls Gradle + `xcodebuild` for you).

### Open a simulator

```text
Xcode → Open Developer Tool → Simulator
File → Open Simulator → iOS → iPhone 16 Pro   (any modern iPhone is fine)
```

The simulator does **not** need to match your physical iPhone model.

### Build and run from Xcode

1. In Android Studio, switch the project view from **Android** to **Project** (top-left dropdown). This makes `iosApp/` visible.
2. Open `iosApp/iosApp.xcodeproj` in Xcode.
3. In Xcode's scheme/device dropdown:
   - Scheme: **iosApp**
   - Destination: an iPhone simulator (e.g. *iPhone 16 Pro*)
4. Click **Run** (`⌘R`).

The first build is slow — Gradle has to produce the `ComposeApp` framework before Xcode can link it.

### What you should see

The starter app inside the simulator window with `Compose: Hello, iOS …`.

### If the first build fails with a Java error

This is the most common first-time problem. See:

- [Troubleshooting → Xcode says "Unable to locate a Java Runtime"](TROUBLESHOOTING.md#xcode-says-unable-to-locate-a-java-runtime)
- [Environment Setup → §7 Force Xcode to use Java 21](ENVIRONMENT_SETUP.md#7-force-xcode-to-use-java-21-for-the-kmp-build)

---

## 4. Physical iPhone

Same Xcode build as the simulator, plus signing.

### One-time prep

See [Environment Setup → §9](ENVIRONMENT_SETUP.md#9-connect-a-physical-iphone-optional) — enable Developer Mode on the iPhone, add your Apple ID to Xcode, configure automatic signing on the **iosApp** target.

### Run

1. Plug the iPhone in and unlock it.
2. In Xcode's destination dropdown, select your physical iPhone.
3. Click **Run** (`⌘R`).

### First-launch trust step

The very first time you install a developer-built app on a real iPhone, iOS shows **Untrusted Developer**. Trust your Apple ID once:

```text
Settings → General → VPN & Device Management
   → tap your Apple ID under DEVELOPER APP → Trust → confirm
```

(On older iOS the path is `Settings → General → Device Management`.)

After that, the app launches normally and future builds don't need this step.

### Run from Android Studio (after Xcode worked once)

Once signing is set up in Xcode, the iPhone often appears in Android Studio's device picker too. Select the **iosApp** run configuration, pick the phone, click **Run**. Android Studio will:

1. Build the framework via Gradle.
2. Hand off to `xcodebuild` for signing/install.
3. Install the app on the phone.

If Android Studio gets stuck on `Verifying iOS …simruntime`, let it finish (first time only) or check **Xcode → Settings → Platforms** to make sure the runtime finished installing.

---

## 5. Desktop (JVM) with Hot Reload

The fastest UI inner-loop. Compose Hot Reload lets you change a composable and see the running window update without restart.

```bash
./gradlew :composeApp:run
```

### What you should see

A native desktop window titled *KMPStarter* with the starter UI and `Compose: Hello, Java …`.

### Iterate

Edit any composable in `composeApp/src/commonMain/` or `composeApp/src/jvmMain/` and save — the running window picks up the change without restart. Use this as your default UI loop, even when the final target is Android or iOS.

### Package a native installer

```bash
./gradlew :composeApp:packageDistributionForCurrentOS
```

Output lands in `composeApp/build/compose/binaries/` — `.dmg` on macOS, `.msi` on Windows, `.deb` on Linux. Cross-OS packaging is not supported (run the task on each target OS).

---

## 6. Web

KMPStarter ships two web targets:

- **Wasm (preferred)** — faster, smaller, modern. Requires a recent browser (Chrome 119+, Firefox 120+, Safari 18.2+).
- **JS (fallback)** — slower and larger, but works on older browsers.

### Run the Wasm dev server

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

It opens `http://localhost:8080` automatically. Hot module replacement is enabled.

### Run the JS dev server

```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```

Same idea, on `http://localhost:8080`.

### What you should see

The starter UI rendered in the browser tab with `Compose: Hello, Web with Kotlin/Wasm` (or `…/JS`).

### Production bundles

```bash
./gradlew :composeApp:wasmJsBrowserDistribution     # composeApp/build/dist/wasmJs/productionExecutable
./gradlew :composeApp:jsBrowserDistribution         # composeApp/build/dist/js/productionExecutable
```

Deploy either folder as a static site (Cloudflare Pages, Netlify, Vercel, S3+CloudFront, GitHub Pages — adjust the `<base href>` in `index.html` for subpath hosting).

---

## Quick reference

| Target | Command / action | Notes |
|---|---|---|
| Android emulator | Android Studio Run, or `./gradlew :composeApp:installDebug` | Boot an emulator first |
| Real Android | Same as emulator after USB debugging is on | `adb devices` to verify |
| iOS simulator | Open `iosApp/iosApp.xcodeproj` → Xcode Run | Needs Java 21 + signed Xcode |
| Real iPhone | Xcode Run after signing setup | Trust developer on first launch |
| Desktop | `./gradlew :composeApp:run` | Hot Reload enabled |
| Web (Wasm) | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` | Preferred web target |
| Web (JS) | `./gradlew :composeApp:jsBrowserDevelopmentRun` | Fallback for older browsers |

For the full Gradle reference (testing, packaging, build inspection, CI hints) see [`../DEVELOPMENT_COMMANDS.md`](../DEVELOPMENT_COMMANDS.md).

If a target doesn't run, check [Troubleshooting](TROUBLESHOOTING.md) — every issue we've actually hit is in there.
