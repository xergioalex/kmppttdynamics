# Environment Setup (macOS)

A step-by-step guide for setting up a Kotlin Multiplatform / Compose Multiplatform development environment on macOS, starting from zero.

By the end of this guide you will be able to build and run KMPTodoApp on:

- Android emulator
- Physical Android device
- iOS simulator
- Physical iPhone
- Desktop (JVM)
- Web (Wasm and JS)

> Already have your tools installed? Skip to [Running the App](RUNNING_THE_APP.md).
> Hit a problem? Check [Troubleshooting](TROUBLESHOOTING.md).

This setup was validated on macOS (Apple Silicon) with Android Studio, Xcode, a physical Samsung Galaxy device, a physical iPhone, and Java 21.

---

## 1. What you need to install

| Tool | Purpose | How to install |
|---|---|---|
| **Android Studio** | Primary IDE for KMP, Android SDK manager, emulator | [Download](https://developer.android.com/studio) |
| **Xcode** | Required to build the iOS framework, sign, run on simulator/device | Mac App Store |
| **Xcode Command Line Tools** | Used by Gradle and `xcodebuild` | `xcode-select --install` |
| **Homebrew** | Package manager (used to install Java) | [brew.sh](https://brew.sh) |
| **Java 21 (Temurin)** | Required by Gradle when Xcode invokes it from the iOS build script | `brew install --cask temurin@21` |
| **Kotlin Multiplatform plugin** | Adds the KMP project template and run configurations to Android Studio | See [§3](#3-install-the-kotlin-multiplatform-plugin) |
| **Git** | Version control | Pre-installed on macOS, or via Homebrew |

Optional:

| Tool | Purpose |
|---|---|
| **Cursor** | AI-assisted editor that opens the project like VS Code |
| **JetBrains Fleet** | Alternative IDE with KMP support |
| **Flutter SDK** | Only if you also want to compare/develop in Flutter — see [Flutter Bonus](FLUTTER_BONUS.md) |

---

## 2. Install Android Studio

1. Download Android Studio from [developer.android.com/studio](https://developer.android.com/studio).
2. Open the `.dmg` and drag Android Studio to `/Applications`.
3. Launch it. On first run it will offer to install:
   - Android SDK
   - Android SDK Platform-Tools (`adb`)
   - Android emulator
   - At least one system image (e.g. API 36)

   Accept the defaults — they are what KMPTodoApp uses (`compileSdk = 36`, `minSdk = 24`).

### Verify it worked

Open Android Studio's **Device Manager** (`View → Tool Windows → Device Manager`) and create a virtual device, e.g. **Medium Phone, API 36**. Boot it once to confirm the emulator works.

From the terminal:

```bash
adb --version
```

Expected output: `Android Debug Bridge version 1.0.x`. If `adb` is not on your `PATH`, add it:

```bash
echo 'export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

---

## 3. Install the Kotlin Multiplatform plugin

Out of the box, Android Studio shows only Android templates (Empty Activity, Basic Views, etc.). The Kotlin Multiplatform template is a separate plugin.

1. Open Android Studio.
2. **Settings** (`⌘,`) → **Plugins** → **Marketplace** tab.
3. Search for `Kotlin Multiplatform`.
4. Install the official plugin published by JetBrains.
5. Restart Android Studio.

### Verify it worked

`File → New → New Project…` should now list **Kotlin Multiplatform** as a project template.

> KMPTodoApp is already a working KMP project, so you don't need to create a new one to develop here. The plugin is still useful for the run configurations it adds (e.g., the iOS device target).

---

## 4. Install Xcode

iOS builds require Xcode — even when you launch the app from Android Studio, the iOS framework is built by Gradle and linked through Xcode.

1. Install **Xcode** from the Mac App Store (it's a multi-GB download).
2. Open Xcode once and accept the license prompt.
3. Install the Command Line Tools:

   ```bash
   xcode-select --install
   ```

4. Make sure Xcode points at the right developer directory:

   ```bash
   sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
   ```

### Install an iOS simulator runtime

Xcode → **Settings** → **Platforms** → install at least one iOS runtime (e.g. iOS 18). The first install is large and can take a while.

### Verify it worked

```bash
xcodebuild -version
xcrun simctl list devices available
```

You should see the Xcode version and at least one available simulator (e.g., `iPhone 16 Pro`).

---

## 5. Install Java 21 (this is the trickiest step)

### Why Java is needed at all

Xcode itself does not need Java to build a normal Swift app. KMPTodoApp, however, has a **"Compile Kotlin Framework"** build phase in `iosApp/iosApp.xcodeproj` that calls `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. Gradle runs on the JVM, so it requires a JDK on `PATH` when Xcode invokes it.

Android Studio works around this by shipping its own bundled JDK. Xcode does **not**, so we install one ourselves and point Xcode at it.

### Why specifically Java 21

The latest JDK from `brew install --cask temurin` (Java 26 at the time of writing) is **too new** for Gradle/Kotlin to handle. You will see a cryptic error:

```text
* What went wrong:
26
```

That single number is Gradle bailing out because it does not recognize the JVM major version. **Use Java 21 (LTS)** — it is supported by every current Gradle and Kotlin version.

### Install Java 21

```bash
brew install --cask temurin@21
```

### Verify the install

```bash
/usr/libexec/java_home -V
```

You should see something like:

```text
Matching Java Virtual Machines (1+):
  21.0.x (arm64) "Eclipse Adoptium" - "OpenJDK 21.0.x"  /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

If you previously installed Java 26 (or any newer version), it can stay — we will explicitly tell Xcode to use 21 in the next section.

To check the JVM your shell is currently using:

```bash
java -version
```

You can either set Java 21 as the global default (optional) or only force it for the iOS build (recommended — see [§7](#7-force-xcode-to-use-java-21-for-the-kmp-build)).

To set it globally for your shell:

```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version    # should report 21.x
```

---

## 6. Clone and open KMPTodoApp

```bash
git clone https://github.com/xergioalex/kmptodoapp.git
cd kmptodoapp
```

Open the project in Android Studio:

- `File → Open…` → select the `kmptodoapp` folder.
- Wait for the **Gradle sync** to finish (status bar at the bottom). The first sync downloads dependencies and can take several minutes.

If sync fails, jump to [Troubleshooting](TROUBLESHOOTING.md).

### Verify with a fast smoke test

Run the JVM tests — they're the fastest target and a good signal that Gradle/Kotlin are configured correctly:

```bash
./gradlew :composeApp:jvmTest
```

Expected: `BUILD SUCCESSFUL`.

---

## 7. Force Xcode to use Java 21 for the KMP build

The `iosApp` Xcode project includes a "Compile Kotlin Framework" run script. Even if you set Java 21 as the global default, it's safer to lock the Xcode build phase to Java 21 explicitly so it never breaks if a teammate has a different default.

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. Click the **iosApp** project in the Project Navigator.
3. Select the **iosApp** target.
4. Go to **Build Phases**.
5. Expand **Run Script** (the one named *Compile Kotlin Framework* — it calls `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`).
6. Add these two lines at the **very top** of the script:

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   export PATH="$JAVA_HOME/bin:$PATH"
   ```

The script should now look like:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
  exit 0
fi
cd "$SRCROOT/.."
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

7. Clean the Xcode build folder: `Product → Clean Build Folder` (`⇧⌘K`).
8. Build with `⌘B` to verify the framework compiles.

> **Why we don't commit this** — the Xcode project file `iosApp/iosApp.xcodeproj/project.pbxproj` is a per-developer concern in this template; you can also commit the change if your whole team uses Java 21. Document it in your fork's README either way.

---

## 8. Connect a physical Android device (optional)

### Enable Developer Options

On most Android devices:

1. **Settings → About phone**.
2. Tap **Build number** seven times.
3. Enter your PIN/password if prompted.

> **Samsung devices**: Build number is under **Settings → About phone → Software information** (not directly under About phone).

You'll see a confirmation that Developer Mode is on.

### Enable USB debugging

1. **Settings → Developer options**.
2. Toggle **USB debugging** on.
3. Plug the phone into the Mac with USB-C.
4. On the phone, tap **Allow** when prompted to authorize the computer for USB debugging. Tick **Always allow from this computer** for convenience.

### Verify

```bash
adb devices
```

You should see your device listed (e.g. `samsung SM-S928B   device`). If it says `unauthorized`, unplug and re-plug, and re-accept the prompt on the phone.

---

## 9. Connect a physical iPhone (optional)

### One-time iPhone setup

1. Connect the iPhone via USB. On the phone, tap **Trust** when asked "Trust this computer?".
2. **Settings → Privacy & Security → Developer Mode → On**. The phone restarts and asks you to confirm Developer Mode again.

### One-time Xcode setup

1. Xcode → **Settings → Accounts → +** → **Add Apple Account…** → sign in with your Apple ID.
2. In `iosApp.xcodeproj`, select the **iosApp** target → **Signing & Capabilities**:
   - Tick **Automatically manage signing**.
   - Pick your **Team** (your Apple ID team will appear).
   - Make sure **Bundle Identifier** is unique to you (e.g. `com.xergioalex.kmptodoapp` — see `// FORK-RENAME:` comments if you've forked this repo).

### Verify

In Xcode's device picker (top toolbar), your iPhone should appear by name. The first build to a real device may show **Untrusted Developer** on the phone — see [Troubleshooting](TROUBLESHOOTING.md#iphone-says-untrusted-developer) for the trust step.

---

## 10. Final sanity checklist

Run these and confirm each succeeds before moving on:

```bash
java -version                                # Should report 21.x (or you've configured per-tool overrides)
./gradlew --version                          # Gradle wrapper works
./gradlew :composeApp:assemble               # Compiles all KMP targets
./gradlew :composeApp:jvmTest                # JVM tests pass (fastest signal)
adb devices                                  # Lists Android devices/emulators
xcrun simctl list devices available          # Lists iOS simulators
```

If everything passes, you're ready to [run the app on each target](RUNNING_THE_APP.md).

If something failed, the [troubleshooting guide](TROUBLESHOOTING.md) covers every issue we've actually hit during setup.
