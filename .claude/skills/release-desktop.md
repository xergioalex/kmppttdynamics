---
name: release-desktop
description: Build a signed and (on macOS) notarized desktop installer
---

# Skill: `/release-desktop`

Build a desktop installer for the current OS. Compose Desktop produces native installers via `jpackage`. Cross-OS packaging is **not** supported — to ship to Windows you need Windows, to macOS you need macOS, etc.

## Inputs to confirm

- **Target OS:** macOS, Windows, or Linux (or all three via CI)
- **New version** (`packageVersion`)
- **Signing setup:**
  - macOS: Developer ID Application certificate, App-Specific Password for notarization
  - Windows: Code signing certificate (EV recommended)
  - Linux: typically unsigned (consider signing the `.deb`)
- **Distribution channel:** direct download, Mac App Store, Microsoft Store, Snap/Flatpak

## Procedure

### 1. Bump version

`composeApp/build.gradle.kts`:

```kotlin
compose.desktop {
    application {
        nativeDistributions {
            packageVersion = "1.0.1"
        }
    }
}
```

### 2. Pre-flight

```bash
./gradlew clean
./gradlew :composeApp:assemble :composeApp:allTests
./gradlew :composeApp:run                   # Smoke-test the dev build
```

### 3. Build the installer

```bash
./gradlew :composeApp:packageDistributionForCurrentOS         # Debug variant
./gradlew :composeApp:packageReleaseDistributionForCurrentOS  # Optimized
```

Output paths:

- macOS: `composeApp/build/compose/binaries/main/dmg/<packageName>-<version>.dmg`
- Windows: `composeApp/build/compose/binaries/main/msi/<packageName>-<version>.msi`
- Linux: `composeApp/build/compose/binaries/main/deb/<packageName>-<version>.deb`

### 4. macOS — sign and notarize

A raw `.dmg` is unsigned and Gatekeeper will block it. To distribute outside dev machines:

#### Sign the app bundle inside the dmg

Compose Desktop can sign automatically if you configure it:

```kotlin
nativeDistributions {
    macOS {
        bundleID = "com.xergioalex.kmptodoapp"
        signing {
            sign.set(true)
            identity.set("Developer ID Application: Your Name (TEAMID)")
        }
        notarization {
            appleID.set(providers.gradleProperty("APPLE_ID"))
            password.set(providers.gradleProperty("APPLE_APP_SPECIFIC_PASSWORD"))
            teamID.set("TEAMID")
        }
    }
}
```

#### Manual signing (alternative)

```bash
# Find the .app bundle inside the .dmg or in the build output
codesign --deep --force --options runtime \
    --sign "Developer ID Application: Your Name (TEAMID)" \
    composeApp/build/compose/binaries/main/app/<package>.app

# Re-create the dmg with the signed app
# (Compose Desktop usually does this; manual signing is rare)
```

#### Notarize

```bash
xcrun notarytool submit composeApp/build/compose/binaries/main/dmg/<file>.dmg \
    --apple-id "you@example.com" \
    --password "$APPLE_APP_SPECIFIC_PASSWORD" \
    --team-id "TEAMID" \
    --wait

xcrun stapler staple composeApp/build/compose/binaries/main/dmg/<file>.dmg
```

The Apple-specific password is generated at appleid.apple.com — never use your account password.

### 5. Windows — code sign

```powershell
signtool sign /f cert.pfx /p $env:CERT_PASSWORD /tr http://timestamp.digicert.com /td sha256 /fd sha256 `
    composeApp/build/compose/binaries/main/msi/<file>.msi
```

EV certificates skip the SmartScreen reputation cooldown that hits standard certs.

### 6. Linux — package validation

```bash
dpkg -I composeApp/build/compose/binaries/main/deb/<file>.deb       # Inspect metadata
dpkg-deb -c composeApp/build/compose/binaries/main/deb/<file>.deb   # List contents
```

For wider distribution consider AppImage (manual) or a Snap / Flatpak (separate build).

### 7. Smoke-test the installer

Install on a clean machine (or VM):

- macOS: open the `.dmg`, drag to Applications, verify Gatekeeper accepts the signature
- Windows: run the `.msi`, verify SmartScreen accepts the signature
- Linux: `sudo dpkg -i <file>.deb`, run from the launcher, verify it appears in the desktop menu

If the OS warns about an untrusted publisher — your signing didn't work or the cert isn't trusted yet (Apple notarization can lag).

### 8. Distribute

Options:

- **Direct download** — host the installer on your website with checksums
- **Mac App Store** — different signing flow (use `MAS` distribution profile, App Sandbox required)
- **Microsoft Store** — submit via Partner Center; typically requires an MSIX package
- **Snap/Flatpak** — separate manifest and submission flow
- **Auto-update** — adopt a library like `Sparkle` (macOS) or `Squirrel` (Windows). Compose-MP doesn't ship auto-update; integrate manually

### 9. Tag and document

```bash
git tag desktop/v1.0.1
git push origin desktop/v1.0.1
```

Update [`docs/BUILD_DEPLOY.md`](../../docs/BUILD_DEPLOY.md) if the release process changed.

## CI

Suggest a matrix that runs on each OS:

```yaml
strategy:
  matrix:
    os: [macos-latest, windows-latest, ubuntu-latest]
runs-on: ${{ matrix.os }}
steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with:
      distribution: temurin
      java-version: '17'
  - run: ./gradlew :composeApp:packageReleaseDistributionForCurrentOS
  # macOS-only: sign + notarize
  - if: matrix.os == 'macos-latest'
    run: |
      # Import certs, sign, notarize
```

Each runner outputs only the installer for its OS.

## Pitfalls

1. **Cross-OS packaging fails.** You can only build a `.msi` on Windows, a `.dmg` on macOS, a `.deb` on Linux
2. **Notarization rejection** — common causes: hardened runtime not enabled, missing entitlements, embedded binary not signed. Read the notarization log
3. **Missing icon files** — set per-OS icons in `nativeDistributions { macOS { iconFile.set(...) } }` etc.
4. **Java runtime bundling** — Compose Desktop includes a JRE in the installer (~50 MB). Don't try to strip it; the user wouldn't have the right JRE otherwise
5. **Different `packageName` than `applicationId`** — confusing but allowed; pick one convention and stick to it

## Don't

- Ship an unsigned macOS dmg outside dev machines (Gatekeeper blocks it)
- Skip Windows signing (SmartScreen warnings drive uninstalls)
- Forget to bump `packageVersion`
- Commit signing credentials

## Do

- Sign and notarize per OS
- Tag the release
- Smoke-test on a clean OS install or VM
- Maintain checksums (`sha256sum`) alongside hosted installers
