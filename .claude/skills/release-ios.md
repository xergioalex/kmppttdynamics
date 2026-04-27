---
name: release-ios
description: Archive and upload an iOS release to TestFlight or the App Store
---

# Skill: `/release-ios`

Cut an iOS release. iOS distribution goes through Xcode — Gradle only produces the framework.

## Inputs to confirm

- **New version** (`CFBundleShortVersionString`, e.g., `1.0.1`)
- **New build number** (`CFBundleVersion`, integer that must increase per upload)
- **Distribution target:** TestFlight, App Store, Ad Hoc, Development
- **Apple Developer Team** is configured in Xcode signing
- **Bundle id** matches what's registered in App Store Connect

## Procedure

### 1. Bump version and build number

In Xcode: project navigator → `iosApp` target → **General** → Identity:

- Version: `1.0.1`
- Build: `2` (or use your CI's build number)

Or edit `iosApp/iosApp.xcodeproj/project.pbxproj` directly:

```
CURRENT_PROJECT_VERSION = 2;
MARKETING_VERSION = 1.0.1;
```

### 2. Pre-flight build

```bash
./gradlew clean
./gradlew :composeApp:assemble :composeApp:allTests
./gradlew :composeApp:linkReleaseFrameworkIosArm64
```

Open Xcode, select **Any iOS Device (arm64)** as the run destination, and Product → Build (⌘B). Resolve any signing or build issues now.

### 3. Smoke-test on a device

Run on a real iPhone before archiving:

- Connect device
- Select it as the run destination
- ⌘R
- Verify the core flows work

### 4. Archive

In Xcode: Product → Archive

Xcode runs Gradle to produce the release framework, then archives. The Organizer window opens when done.

If Archive grays out, check that the destination is a real device or "Any iOS Device" — archives can't be made against simulators.

### 5. Validate

Organizer → select the archive → **Validate App**:

- Pick the distribution method (App Store Connect)
- Choose signing options (typically Automatic)
- Address any validation errors before proceeding

Common validation issues:

- **Missing required device capabilities** — check `Info.plist`
- **App icons missing** — make sure `Assets.xcassets/AppIcon.appiconset` has all required sizes
- **Privacy manifest missing** — add `PrivacyInfo.xcprivacy` if your SDKs require it (iOS 17+)

### 6. Distribute

Organizer → **Distribute App**:

- **App Store Connect** → uploads to App Store Connect for TestFlight / App Store
- **Ad Hoc** → produces an `.ipa` for installation on registered devices
- **Development** → produces an `.ipa` for development testing

### 7. TestFlight

After uploading via App Store Connect distribution:

1. Wait for the build to finish processing (10-30 minutes)
2. Open App Store Connect → My Apps → your app → **TestFlight**
3. The new build appears under iOS builds
4. Add internal or external testers
5. External tester groups require Beta App Review for the *first build* of each version (~24h)

### 8. App Store

When the build is approved by you in TestFlight:

1. App Store Connect → your app → **App Store** tab
2. Create a new version (e.g., `1.0.1`)
3. Add the build to the version
4. Fill out "What's New" in every supported localization
5. Submit for review

Apple review usually takes 24-48 hours.

### 9. Tag and document

```bash
git tag ios/v1.0.1
git push origin ios/v1.0.1
```

Update [`docs/BUILD_DEPLOY.md`](../../docs/BUILD_DEPLOY.md) if anything in the process changed.

## Command-line release (CI)

```bash
# Produce release framework
./gradlew :composeApp:linkReleaseFrameworkIosArm64

# Archive
xcodebuild -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Release \
    -destination "generic/platform=iOS" \
    -archivePath build/iosApp.xcarchive \
    archive

# Export
xcodebuild -exportArchive \
    -archivePath build/iosApp.xcarchive \
    -exportPath build/ipa \
    -exportOptionsPlist exportOptions.plist
```

`exportOptions.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store</string>
    <key>teamID</key>
    <string>YOUR_TEAM_ID</string>
    <key>destination</key>
    <string>upload</string>
</dict>
</plist>
```

For CI, use Fastlane / `xcrun altool` or `xcrun notarytool` for upload. Store the certificates and provisioning profiles in the CI's secret store (GitHub Actions: `apple-actions/import-codesign-certs`).

## Pitfalls

1. **Build number not increasing** — App Store Connect rejects duplicate build numbers per version. Always bump
2. **Signing & Capabilities mismatch** — automatic signing usually works, but if you switch teams or capabilities (push, biometrics) you need fresh provisioning
3. **Missing privacy manifest** — iOS 17+ rejects builds without `PrivacyInfo.xcprivacy` for SDKs accessing required-reason APIs
4. **Different display name across locales** — Xcode supports per-language `InfoPlist.strings` for `CFBundleDisplayName`; if you ship in multiple locales, add them
5. **Compose-MP framework changed name** — if you renamed the framework, update `import` in `ContentView.swift`

## Don't

- Skip validation in the Organizer — App Store Connect rejects faster, but Apple's review queue is slower
- Submit a build with debug-only logging that includes PII
- Forget to bump the build number
- Use the same build number across two App Store Connect uploads

## Do

- Tag the release in git (`ios/vX.Y.Z`)
- Smoke-test on a real device before archiving
- Provide release notes for every locale
- Keep your provisioning profiles renewed (they expire annually)
