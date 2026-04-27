# Flutter Setup (Bonus / Comparison)

KMPTodoApp is a **Kotlin Multiplatform** template, not a Flutter project. This doc exists because both stacks target the same platforms (Android, iOS, Web, Desktop), and developers evaluating KMP often want to also try Flutter side by side.

If you only care about KMP, you can skip this entirely.

---

## What's actually different

The build pipelines look very different even when the output looks similar.

### Kotlin Multiplatform

```text
Android Studio + Xcode
  → Gradle / Kotlin
  → Shared Kotlin (commonMain) + Compose Multiplatform UI
  → Per-platform outputs:
       Android (APK / AAB)
       iOS (static framework consumed by a Swift wrapper)
       Desktop (JVM binary, native installer via jpackage)
       Web (Wasm or JS bundle served as a static site)
```

You stay close to native tooling: `gradle`, `xcodebuild`, AGP, Kotlin/Native. That's a feature when you need deep platform integration; it's friction when you just want a "hello world" running on five targets.

### Flutter

```text
Flutter SDK
  → Dart
  → Flutter engine (renders via Skia/Impeller)
  → Per-platform outputs (managed by `flutter` CLI)
```

The `flutter` CLI handles emulators, devices, hot reload, signing setup, and packaging. The first "hello world" experience is usually smoother because the CLI hides the per-platform tooling.

### Tradeoff in one line

> **KMP**: share what makes sense, stay close to native. **Flutter**: share most of the app, abstract away from native.

---

## Installing Flutter on macOS

```bash
brew install --cask flutter
```

Then run the doctor:

```bash
flutter doctor
```

`flutter doctor` checks every layer Flutter needs and tells you what's missing. Typical first run on a fresh Mac:

```text
[✓] Flutter (Channel stable, …)
[!] Android toolchain - some Android licenses not accepted.
[✓] Xcode - develop for iOS and macOS (Xcode 16.x)
[✓] Chrome - develop for the web
[✓] Android Studio (version …)
[✓] Connected device (… available)
[✓] Network resources
```

### Accept Android licenses

```bash
flutter doctor --android-licenses
```

Press `y` until it returns. Re-run `flutter doctor` — that line should now be green.

### What the green check actually verifies

| Doctor line | What it confirms |
|---|---|
| Flutter | Flutter SDK is installed and on `PATH` |
| Android toolchain | Android SDK + licenses + `adb` reachable |
| Xcode | Xcode + Command Line Tools + license accepted |
| Chrome | Web target dependencies (Chrome installed) |
| Android Studio | Flutter plugin installed in IDE (only after you install it — see below) |
| Connected device | At least one runnable device (emulator, simulator, browser, real device) |

---

## Flutter plugin in Android Studio

To create / run Flutter projects from Android Studio:

1. **Settings → Plugins → Marketplace**.
2. Search `Flutter`. Install. The Dart plugin installs alongside it automatically.
3. Restart Android Studio.

You can now `New Flutter Project…` and run with the IDE's Flutter device picker.

---

## Targets you'll see after setup

Once `flutter doctor` is green and a device is connected:

```bash
flutter devices
```

Typical output on a Mac:

```text
Chrome (web)              • chrome  • web-javascript
Medium Phone API 36 (mobile) • emulator-5554 • android-arm64
samsung SM-S928B (mobile) • <serial> • android-arm64
iPhone 16 Pro (mobile)    • <udid>  • ios
macOS (desktop)           • macos   • darwin-arm64
```

Run on a specific device:

```bash
flutter run -d chrome
flutter run -d <android-id>
flutter run -d <ios-udid>
flutter run -d macos
```

---

## When devices don't appear

| Symptom | Check |
|---|---|
| Android phone missing | `adb devices` — same checklist as KMP ([Troubleshooting](TROUBLESHOOTING.md#physical-android-device-doesnt-appear-in-android-studio)) |
| iPhone missing | Xcode signing set up, Developer Mode on phone enabled (same prereqs as KMP) |
| Chrome missing | Chrome not installed, or `flutter config --no-enable-web` was run earlier — re-enable with `flutter config --enable-web` |
| macOS desktop missing | `flutter config --enable-macos-desktop` |

The **good news**: every device-level fix you applied to make KMP work (USB debugging, Apple ID signing, Developer Mode) also makes Flutter work. The OS-level layer is shared.

---

## Side-by-side comparison

| Topic | Kotlin Multiplatform | Flutter |
|---|---|---|
| Language | Kotlin (+ Swift / JS interop where needed) | Dart |
| UI | Compose Multiplatform (or per-platform native UIs) | Flutter Widgets (single rendering engine) |
| iOS build | Xcode + Gradle + Kotlin/Native framework | `flutter build ios` (CLI orchestrates Xcode) |
| Native closeness | High — direct access to `UIKit`, `Context`, JVM APIs via `expect`/`actual` | Lower — you go through platform channels |
| First-run setup | More technical (Java/Xcode plumbing) | Smoother (CLI hides most of it) |
| Hot reload | Desktop (Compose Hot Reload) and Android (with limitations) | All targets, very fast |
| Native UI option | Yes — UI can be entirely native, only logic shared | No — you ship Flutter UI |
| Best for | Mobile-first teams that value native integration | Teams optimizing for shared UI velocity |
| Sweet spot | Adding shared logic to existing native apps; new apps where deep platform fit matters | Greenfield apps where the same UI on every platform is the goal |

Neither is "better" in the abstract — they optimize for different things. KMP gives you native ceiling; Flutter gives you portability floor.

---

## Should you switch?

If your team already ships native Android + iOS apps and wants to share the data layer / business logic without losing native UI, **KMP**. If you're building a brand-new app and the UI being identical on every platform is a feature (not a bug), **Flutter** is often faster to a first release.

KMPTodoApp exists because the KMP path has more upfront friction; documenting that friction (this `docs/getting-started/` folder) is part of making the choice fair.
