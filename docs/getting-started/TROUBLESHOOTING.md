# Troubleshooting

Real problems hit while setting KMPStarter up on macOS, with the exact fix for each. If you're hitting something not listed here, open an issue.

> Most setup pain comes from the iOS path (Xcode → Gradle → Java → Kotlin/Native). When in doubt, check Java first.

---

## Kotlin Multiplatform template not visible in Android Studio

**Symptom**: `File → New → New Project…` shows only Android templates (Empty Activity, Basic Views, etc.) — no Kotlin Multiplatform option.

**Cause**: The KMP plugin is a separate JetBrains plugin, not part of the default Android Studio install.

**Fix**:

1. **Settings → Plugins → Marketplace**.
2. Search `Kotlin Multiplatform`.
3. Install the official plugin published by JetBrains.
4. Restart Android Studio.

The KMP template now appears under `New Project`. (KMPStarter is already a working KMP project, so you don't need this just to develop here — but the plugin also adds run configurations for iOS targets, which is useful.)

---

## Xcode says "Unable to locate a Java Runtime"

**Symptom**:

```text
Command PhaseScriptExecution failed with a nonzero exit code
The operation couldn't be completed. Unable to locate a Java Runtime.
```

…during a build of `iosApp.xcodeproj`.

**Cause**: The Xcode project has a *Compile Kotlin Framework* run script that calls `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. Gradle needs a JDK on `PATH`. Android Studio bundles its own JDK; Xcode does not.

**Fix**:

1. Install Java 21 if you don't have it:

   ```bash
   brew install --cask temurin@21
   ```

2. Either set Java 21 as your shell default:

   ```bash
   echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
   echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
   source ~/.zshrc
   ```

3. **Or (recommended)** force the Xcode build script itself to use Java 21 — see [Environment Setup → §7](ENVIRONMENT_SETUP.md#7-force-xcode-to-use-java-21-for-the-kmp-build).

4. In Xcode: `Product → Clean Build Folder` (`⇧⌘K`), then build again.

---

## Gradle fails with `What went wrong: 26` (or another number)

**Symptom**:

```text
* What went wrong:
26
```

…or a similar one-line "stacktrace" with just a JDK major version.

**Cause**: Gradle/Kotlin doesn't recognize the Java version it's running on. Brew's default `temurin` cask installs the latest JDK (Java 26 at the time of writing), which is too new for the Gradle/Kotlin tooling KMPStarter uses.

**Fix**: Install **Java 21 (LTS)** and point the build at it.

```bash
brew install --cask temurin@21
/usr/libexec/java_home -V    # verify 21 is listed
```

Then either set it as your shell default (see above) or pin it in `iosApp.xcodeproj`'s Compile Kotlin Framework script (see [Environment Setup → §7](ENVIRONMENT_SETUP.md#7-force-xcode-to-use-java-21-for-the-kmp-build)).

You can leave Java 26 installed alongside 21 — `/usr/libexec/java_home -v 21` always returns the 21 install path regardless of what's "default".

---

## iPhone says "Untrusted Developer"

**Symptom**: First launch on a real iPhone shows a dialog:

```text
Untrusted Developer
Your device management settings do not allow apps from developer "Apple Development: …" on this iPhone.
```

**Cause**: iOS requires explicit user trust for apps signed with a personal/free Apple ID before they can run.

**Fix**: On the iPhone:

```text
Settings → General → VPN & Device Management
   → DEVELOPER APP: tap your Apple ID → Trust → confirm
```

(On older iOS versions: `Settings → General → Device Management`.)

You only need to do this once per developer profile per device. Re-installs of the same app no longer prompt.

---

## Physical Android device doesn't appear in Android Studio

**Symptom**: The phone is plugged in but Android Studio's device dropdown doesn't list it. Or `adb devices` shows nothing / `unauthorized`.

**Checklist**:

1. **Developer Options enabled?**
   `Settings → About phone → Build number` (tap 7×).
   *Samsung devices*: `Settings → About phone → Software information → Build number`.

2. **USB debugging enabled?**
   `Settings → Developer options → USB debugging`.

3. **Authorized this Mac?** A prompt should appear on the phone when you plug in. Tap **Allow** (and tick *Always allow from this computer*).

4. **Cable is data-capable?** Some USB-C cables are charge-only. Try a different cable.

5. **`adb` working?**

   ```bash
   adb kill-server && adb start-server
   adb devices
   ```

   You should see your device with status `device` (not `unauthorized` or `offline`).

6. **`adb` on PATH?**

   ```bash
   echo 'export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"' >> ~/.zshrc
   source ~/.zshrc
   ```

If status is `unauthorized` after all of the above, revoke USB debugging authorizations on the phone (`Developer options → Revoke USB debugging authorizations`), unplug, plug back in, accept the new prompt.

---

## iOS simulator doesn't appear in Android Studio

**Symptom**: The iOS run configuration in Android Studio shows no available simulators, or simulators that worked before disappeared.

**Cause**: Android Studio relies on Xcode's installed simulator runtimes. If Xcode is mid-update, hasn't finished verifying a runtime, or has no runtimes installed, Android Studio sees nothing.

**Fix**:

1. Open Xcode → **Settings → Platforms**.
2. Make sure at least one iOS runtime is installed and shows as ready (not *Verifying* or *Downloading*).
3. From the terminal:

   ```bash
   xcrun simctl list devices available
   ```

   You should see at least one available simulator (e.g. *iPhone 16 Pro*).

4. Reliable workaround: open `iosApp/iosApp.xcodeproj` in Xcode and run from there. Once the build works in Xcode, restart Android Studio — the simulator usually reappears.

---

## Xcode is stuck on "Verifying iOS …simruntime" for a long time

**Cause**: First-time runtime install/verify after installing or updating Xcode. The runtime is several GB and can take a while.

**Fix**:

- Be patient — let it finish at least once.
- Watch progress in **Xcode → Settings → Platforms**.
- If it appears genuinely stuck (no progress for >30 min, no network activity), quit Xcode, restart it, and re-trigger the install.

---

## "Configuration cache problems" or generally weird Gradle state

**Symptom**: Gradle errors that don't make sense — references to tasks that don't exist, missing source sets, hot reload misbehaving.

**Fix**: Reset the build state:

```bash
./gradlew --stop
rm -rf .gradle composeApp/build build
./gradlew :composeApp:assemble
```

If that doesn't help, also clear the global Gradle caches (heavy — last resort):

```bash
rm -rf ~/.gradle/caches
```

---

## Gradle sync fails on first open

**Symptom**: Android Studio opens KMPStarter and Gradle sync fails with dependency-resolution or plugin-not-found errors.

**Common causes**:

- **No internet on first sync** — Gradle needs to download dependencies the first time. Make sure you're online.
- **Wrong JDK in Android Studio** — `Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK` should be 17 or higher (Android Studio's bundled JDK works).
- **Stale wrapper** — try `./gradlew --stop && ./gradlew :composeApp:assemble` from the terminal to see the real error without the IDE wrapper.

---

## Running on iPhone fails with provisioning / signing errors

**Symptom**: Xcode build fails with messages about missing provisioning profiles, code signing, or *No account for team ""*.

**Fix**:

1. Xcode → **Settings → Accounts** → make sure your Apple ID is added.
2. In `iosApp.xcodeproj` → **iosApp** target → **Signing & Capabilities**:
   - Check **Automatically manage signing**.
   - Pick your team from the dropdown.
   - Make sure **Bundle Identifier** is unique (Apple's free signing requires globally unique IDs). If you're forking, change it from `com.xergioalex.kmpstarter` to your own — see [`../FORK_CUSTOMIZATION.md`](../FORK_CUSTOMIZATION.md).
3. Plug the iPhone in, unlock, and rebuild.

---

## Hot reload (Desktop) not picking up changes

**Symptom**: `./gradlew :composeApp:run` is running, you edit a composable, but the window doesn't update.

**Fix**:

- Make sure you saved the file (`⌘S` in Android Studio if auto-save is off).
- Some changes (adding a new top-level composable, changing function signatures) require a recompile cycle but should still apply without restart.
- Structural changes (Gradle, dependencies, `expect`/`actual` declarations) **do** require a restart — stop the run task and re-run it.
- If the reload pipeline is wedged: `./gradlew --stop` and try again.

---

## Still stuck?

1. Re-read the relevant section of [Environment Setup](ENVIRONMENT_SETUP.md) — most issues come from a missed step.
2. Run the sanity-check commands from [Environment Setup → §10](ENVIRONMENT_SETUP.md#10-final-sanity-checklist) to isolate which layer is broken.
3. Open `iosApp/iosApp.xcodeproj` in Xcode and try building from there — Xcode's error messages are usually more informative than Android Studio's KMP run config.
4. File an issue with the full error, your `java -version`, your `xcodebuild -version`, and your Android Studio version.
