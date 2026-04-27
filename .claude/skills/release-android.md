---
name: release-android
description: Produce a signed Android release (APK and/or AAB) ready for Play upload
---

# Skill: `/release-android`

Produce a signed Android release. The starter ships with `isMinifyEnabled = false` and **no signing config** — both must be addressed before the first real release.

## Inputs to confirm

- **Version bump:** new `versionCode` (must increase) and `versionName`
- **Output:** APK (direct distribution) or AAB (Play Store — preferred)
- **Signing keystore exists?** Path, alias, passwords (stored outside the repo)
- **R8 config:** OK with the recommended R8 setup, or already customized?

## Procedure

### 1. Bump version

Edit `composeApp/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        versionCode = 2          // Was 1; must increase per upload
        versionName = "1.0.1"    // SemVer or your own scheme
    }
}
```

### 2. Enable R8 (first time only)

```kotlin
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

Create `composeApp/proguard-rules.pro`:

```proguard
# Compose Multiplatform / Kotlin/Native generated classes
-keep class com.xergioalex.kmpstarter.** { *; }
-dontwarn org.jetbrains.compose.**

# Add per-library rules from each library's docs as you adopt them:
# Ktor, kotlinx-serialization, etc.
```

### 3. Configure signing (first time only)

Store credentials outside the repo. Add to `~/.gradle/gradle.properties`:

```properties
KMPSTARTER_KEYSTORE_FILE=/Users/you/keys/kmpstarter-release.keystore
KMPSTARTER_KEYSTORE_PASSWORD=...
KMPSTARTER_KEY_ALIAS=kmpstarter
KMPSTARTER_KEY_PASSWORD=...
```

Wire signing in `composeApp/build.gradle.kts`:

```kotlin
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
            // ... existing R8 / minify config
        }
    }
}
```

### 4. Pre-flight checks

```bash
./gradlew :composeApp:assemble :composeApp:allTests
```

Run a debug install on a real device to smoke-test:

```bash
./gradlew :composeApp:installDebug
```

### 5. Build

```bash
./gradlew :composeApp:bundleRelease       # AAB → composeApp/build/outputs/bundle/release/
./gradlew :composeApp:assembleRelease     # APK → composeApp/build/outputs/apk/release/
```

### 6. Smoke-test the signed build

Install the release APK on a device:

```bash
adb install -r composeApp/build/outputs/apk/release/composeApp-release.apk
```

Verify the app launches, no R8-stripped classes throw `ClassNotFoundException`, and core flows work. Don't skip this — R8 surprises happen.

If something is stripped:

- Check the stack trace for the missing class
- Add a `-keep class com.example.LibraryClass { *; }` rule to `proguard-rules.pro`
- Re-build and re-test

### 7. Upload

Play Console → Release → Production (or Internal/Closed/Open testing track first):

1. Create a new release
2. Upload the AAB
3. Add release notes (English + every locale you support)
4. Submit for review

For the first release, also fill out:

- App content (privacy policy URL, target audience, ads, content rating)
- Store listing (description, screenshots, feature graphic)
- App access (login credentials for the Play reviewer if needed)

### 8. Tag and document

```bash
git tag v1.0.1
git push origin v1.0.1
```

Update [`docs/BUILD_DEPLOY.md`](../../docs/BUILD_DEPLOY.md) if the release process changed (e.g., new signing flow, CI integration).

## Pitfalls

1. **`versionCode` collision** — Play rejects uploads with a `versionCode` already used. Always bump
2. **R8 stripping a library used via reflection** — add keep rules from the library's docs
3. **Keystore loss** — without the keystore you can't update the app on Play. Back it up offsite (encrypted)
4. **Locale-specific release notes** — Play requires translations for every locale you list as supported. Use the same language list as your `composeResources/values-*` folders
5. **Targeted SDK below current Play requirement** — Play increases the required `targetSdk` annually. Check before release

## CI

Suggested GitHub Actions skeleton (Linux runner):

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '17'
- run: ./gradlew :composeApp:bundleRelease
  env:
    KMPSTARTER_KEYSTORE_FILE: ${{ secrets.KEYSTORE_FILE_PATH }}
    KMPSTARTER_KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KMPSTARTER_KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KMPSTARTER_KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
- uses: r0adkll/upload-google-play@v1
  with:
    serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
    packageName: com.xergioalex.kmpstarter
    releaseFiles: composeApp/build/outputs/bundle/release/composeApp-release.aab
    track: internal
```

Decode the keystore from a base64 secret in a setup step. Document the CI in [`docs/BUILD_DEPLOY.md`](../../docs/BUILD_DEPLOY.md).

## Don't

- Ship a release with `isMinifyEnabled = false` (bloated APK)
- Commit the keystore or the gradle properties with passwords
- Skip the smoke test of the signed build (R8 issues only surface here)
- Use a `versionCode` that isn't strictly increasing

## Do

- Bump `versionCode` and `versionName` in the same commit as the release
- Tag the release in git
- Smoke-test the signed APK on a real device before uploading to Play
- Maintain release notes per locale
