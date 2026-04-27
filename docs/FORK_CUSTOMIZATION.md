# Fork Customization

Step-by-step rebrand of KMPTodoApp into a new product. This is the first thing you should do after cloning. Every step is mechanical — none of it requires creative judgment.

The placeholders to replace, in summary:

| Placeholder | Currently | Examples of new value |
|---|---|---|
| Project name | `KMPTodoApp` | `MyApp`, `Acme Tasks` |
| Display name (user-visible) | `KMPTodoApp` | `My App`, `Acme Tasks` |
| Package | `com.xergioalex.kmptodoapp` | `com.acme.tasks` |
| Application id (Android) | `com.xergioalex.kmptodoapp` | `com.acme.tasks` |
| Namespace (Android) | `com.xergioalex.kmptodoapp` | `com.acme.tasks` |
| Bundle id (iOS) | (Xcode default) | `com.acme.tasks` |
| Desktop package | `com.xergioalex.kmptodoapp` | `com.acme.tasks` |
| iOS framework name | `ComposeApp` | usually keep, optional rename |
| Kotlin module name | `KMPTodoApp` (`rootProject.name`) | matches project name |
| Generated resources package | `kmptodoapp.composeapp.generated.resources` | `tasks.composeapp.generated.resources` |

## Step 1 — Project name

Edit `settings.gradle.kts`:

```kotlin
rootProject.name = "MyApp"
```

This affects the Gradle module name and the generated resources package prefix.

## Step 2 — Application id, namespace (Android)

Edit `composeApp/build.gradle.kts`:

```kotlin
android {
    namespace = "com.acme.tasks"
    defaultConfig {
        applicationId = "com.acme.tasks"
        // versionCode, versionName, etc.
    }
}
```

Both should match your final published id on Google Play. Once published, **never change** `applicationId` — it's the Play Store identity.

## Step 3 — Compose Desktop package

Edit `composeApp/build.gradle.kts`:

```kotlin
compose.desktop {
    application {
        mainClass = "com.acme.tasks.MainKt"
        nativeDistributions {
            packageName = "com.acme.tasks"
            packageVersion = "1.0.0"
        }
    }
}
```

`mainClass` must match the `package` declared in `jvmMain/main.kt` once you rename the Kotlin packages.

## Step 4 — Rename Kotlin packages

The package `com.xergioalex.kmptodoapp` appears in every source set. Rename in this order:

1. **In your IDE:** Right-click the package in `commonMain/kotlin/com/xergioalex/kmptodoapp/` → Refactor → Rename → `com.acme.tasks`. Repeat for each source set (`androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`, `webMain`, `commonTest`).
2. **Or by hand:** move the directories, then update every `package` line and `import` line.

```bash
# Rough sketch — adjust to your shell
find composeApp/src -type d -name kmptodoapp
# Move each directory:
mv composeApp/src/commonMain/kotlin/com/xergioalex/kmptodoapp composeApp/src/commonMain/kotlin/com/acme/tasks
# Repeat for every source set, then sweep the source files:
grep -rln "com.xergioalex.kmptodoapp" composeApp/src | xargs sed -i '' 's/com\.xergioalex\.kmptodoapp/com.acme.tasks/g'
```

The IDE rename is safer — it updates references atomically.

## Step 5 — Android resources

Edit `composeApp/src/androidMain/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Acme Tasks</string>
</resources>
```

This is the launcher label on Android. If you want different default-vs-localized labels, add `values-<locale>/strings.xml` siblings.

The launcher icons live in `composeApp/src/androidMain/res/mipmap-*/` and `drawable*/ic_launcher_*`. Replace them with your own (Android Studio's "Image Asset" wizard generates the full set).

## Step 6 — Generated resources package

Compose Multiplatform generates the `Res` object under a package derived from the project name. With `rootProject.name = "MyApp"`, generated imports become:

```kotlin
import myapp.composeapp.generated.resources.Res
import myapp.composeapp.generated.resources.app_name
```

After step 1, run a clean build and update every import that referenced `kmptodoapp.composeapp.generated.resources.*`:

```bash
grep -rln "kmptodoapp.composeapp.generated.resources" composeApp/src
```

If you'd rather pick the package explicitly (recommended for stability), set:

```kotlin
// composeApp/build.gradle.kts
compose.resources {
    packageOfResClass = "com.acme.tasks.resources"
}
```

Then imports become `import com.acme.tasks.resources.Res`.

## Step 7 — iOS

In Xcode, open `iosApp/iosApp.xcodeproj`. Change:

- Project → `iosApp` target → **General** → Display Name (`CFBundleDisplayName` in Info.plist)
- Project → `iosApp` target → **General** → Bundle Identifier (`PRODUCT_BUNDLE_IDENTIFIER`) — typically `com.acme.tasks`
- Project → `iosApp` target → **Signing & Capabilities** → Team
- App icons in `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/`

If you want to rename the iOS framework from `ComposeApp` (rarely worth doing — it's just a Kotlin/Native artifact name), update:

1. `composeApp/build.gradle.kts`: `iosTarget.binaries.framework { baseName = "MyAppFramework" }`
2. Xcode project: change `import ComposeApp` to `import MyAppFramework` in `iosApp/iosApp/ContentView.swift`
3. Xcode build settings: search for `ComposeApp` and update references

**Recommendation:** keep `ComposeApp` — it's an internal name, no user sees it.

## Step 8 — Desktop installer metadata

`composeApp/build.gradle.kts`:

```kotlin
compose.desktop {
    application {
        nativeDistributions {
            packageName = "com.acme.tasks"
            packageVersion = "1.0.0"
            // Optional:
            description = "Task manager for Acme team"
            copyright = "© 2026 Acme Inc. All rights reserved."
            vendor = "Acme Inc."
        }
    }
}
```

Add a desktop icon by setting `iconFile.set(file("desktop/icon.icns"))` (macOS), `windows.iconFile.set(file("desktop/icon.ico"))`, etc.

## Step 9 — Window title

`composeApp/src/jvmMain/kotlin/.../main.kt`:

```kotlin
Window(onCloseRequest = ::exitApplication, title = "Acme Tasks") {
    App()
}
```

## Step 10 — Web

`composeApp/src/webMain/resources/index.html`:

```html
<title>Acme Tasks</title>
```

Replace the loading-spinner SVG with your own loader if desired. If you'll host under a subpath (e.g., `https://acme.com/tasks/`), set:

```html
<base href="/tasks/">
```

## Step 11 — README and docs

- Update [`README.md`](../README.md) with the new product name, purpose, and any product-specific run notes
- Update [`AGENTS.md`](../AGENTS.md) Project Overview section
- Search docs for `KMPTodoApp`, `kmptodoapp`, `xergioalex`, `com.xergioalex.kmptodoapp`:

```bash
grep -rln "KMPTodoApp\|kmptodoapp\|xergioalex" --include="*.md" --include="*.kts" --include="*.kt" --include="*.xml" --include="*.html"
```

Replace with your project's identifiers.

## Step 12 — Git remote and history

Optional but typical:

```bash
git remote remove origin
git remote add origin git@github.com:acme/tasks.git
git push -u origin main
```

If you want a clean history:

```bash
rm -rf .git
git init
git add .
git commit -m "chore: bootstrap from KMPTodoApp starter"
```

## Step 13 — License and ownership

The starter doesn't ship a `LICENSE`. Add one — typical choices for an internal product:

- **Proprietary** — keep no LICENSE file (default closed-source) and add a notice in `README.md`
- **MIT / Apache-2.0** — for open-source forks

If you adopt a license, mention it in `composeApp/build.gradle.kts` desktop `nativeDistributions` block.

## Step 14 — Sanity check

```bash
./gradlew clean
./gradlew :composeApp:assemble                          # All targets compile
./gradlew :composeApp:allTests                          # All tests pass
./gradlew :composeApp:run                               # Desktop launches with new title
./gradlew :composeApp:assembleDebug                     # Android APK builds
./gradlew :composeApp:wasmJsBrowserDevelopmentRun       # Web bundle builds
```

Open the Xcode project and run on a simulator to verify the iOS side.

## Step 15 — First commit

```bash
git status     # Verify only intentional changes
git add .
git commit -m "feat: rebrand starter to Acme Tasks"
```

## Checklist

- [ ] `rootProject.name` updated
- [ ] Android `applicationId` and `namespace` updated
- [ ] Desktop `mainClass`, `packageName` updated
- [ ] All Kotlin packages renamed
- [ ] Android `strings.xml` `app_name` updated
- [ ] Android launcher icons replaced
- [ ] Generated `Res` import paths updated everywhere
- [ ] iOS bundle id, display name, signing team set in Xcode
- [ ] iOS app icons replaced
- [ ] Desktop window title and installer metadata updated
- [ ] Web `<title>` updated
- [ ] README and AGENTS.md project overview updated
- [ ] All five `:composeApp:run` / build tasks succeed
- [ ] Optional: git history reset, new remote configured, license added

## When something goes wrong

- **`Unresolved reference: Res`** — clean build (`./gradlew clean`) and verify the generated package path matches the `import` statements
- **`Class not found: com.xergioalex.kmptodoapp.MainKt`** — `mainClass` in `compose.desktop.application` doesn't match the Kotlin package
- **iOS build fails with `No such module 'ComposeApp'`** — re-link the framework: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`, then rebuild Xcode
- **Android can't find the launcher** — `MainActivity` activity name in `AndroidManifest.xml` is relative (`.MainActivity`) so it inherits the namespace; verify `namespace` in `build.gradle.kts` is correct

## After you're done

Once the rebrand is solid, audit the docs:

- Remove sections in `docs/PLATFORMS.md` for any target you don't ship
- Remove the AndroidX libraries you don't use from `gradle/libs.versions.toml`
- Refresh `docs/TECHNOLOGIES.md` to reflect your stack choices
- Update `AGENTS.md` "Project Overview" with the new product description
