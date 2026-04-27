---
name: fork-rebrand
description: Walk a fresh fork through package, applicationId, namespace, bundle id, app name
---

# Skill: `/fork-rebrand`

The user just forked KMPStarter and wants to make it their product. Walk through the rebrand checklist mechanically — no creative judgment.

## Defer to the dedicated doc

Most of this skill is "follow [`docs/FORK_CUSTOMIZATION.md`](../../docs/FORK_CUSTOMIZATION.md) step by step". That doc is the source of truth; this skill is the agent-side procedure.

## Inputs to confirm with the user

Ask before editing anything:

1. **New project name** (e.g., "Acme Tasks") — affects `rootProject.name`, window titles, README
2. **New display name** for end users (often same as project name, sometimes more user-friendly)
3. **New package** (e.g., `com.acme.tasks`) — used for Kotlin packages, Android `applicationId` and `namespace`, Desktop `packageName`, iOS bundle id
4. **iOS bundle id** — usually matches the package
5. **Targets to keep** — does the user want all five (Android, iOS, Desktop, Wasm, JS)? Drop unused ones to simplify
6. **Reset git history?** — fresh `git init` vs keeping the starter's commit history
7. **License** — proprietary, MIT, Apache-2.0, etc.

Don't proceed until you have all of these in writing.

## Procedure

Follow the steps in [`docs/FORK_CUSTOMIZATION.md`](../../docs/FORK_CUSTOMIZATION.md):

1. Update `rootProject.name` in `settings.gradle.kts`
2. Update `applicationId` and `namespace` in `composeApp/build.gradle.kts`
3. Update Compose Desktop `mainClass` and `packageName`
4. Rename Kotlin packages across every source set
5. Update Android `strings.xml` `app_name`
6. Update generated resources package (or set `compose.resources { packageOfResClass = "..." }`)
7. iOS: Xcode display name, bundle id, signing team, app icons
8. Desktop installer metadata
9. Window title
10. Web `<title>` and optional `<base href>`
11. README and AGENTS.md project overview
12. Optional: reset git history, set new remote
13. License file
14. Sanity-check builds for every target the user keeps
15. First commit

## Use the IDE refactor for package renames

Renaming `com.xergioalex.kmpstarter` → `com.acme.tasks` across every source set is error-prone with `sed`. Prefer:

1. Open the project in Android Studio / IntelliJ / Fleet
2. Right-click the `kmpstarter` package node → Refactor → Rename → `tasks`
3. Apply for every source set (the IDE may handle them all in one pass)
4. Repeat the parent-package rename: `com.xergioalex` → `com.acme`

If the IDE isn't available, use a careful `sed` invocation:

```bash
grep -rln "com.xergioalex.kmpstarter" composeApp/src \
  | xargs sed -i '' 's/com\.xergioalex\.kmpstarter/com.acme.tasks/g'
# Then move the directories:
find composeApp/src -type d -name kmpstarter | while read d; do
  parent=$(dirname "$d")
  mv "$parent/xergioalex/kmpstarter" "$parent/../tasks"
  rmdir "$parent" 2>/dev/null
done
```

(Verify each move; the loop above is illustrative — adjust to your structure.)

## Generated resources package

After renaming `rootProject.name`, the generated `Res` lives under a package derived from the new name. If you want stability, set explicitly:

```kotlin
// composeApp/build.gradle.kts
compose.resources {
    packageOfResClass = "com.acme.tasks.resources"
}
```

Then update every `import` accordingly. Search:

```bash
grep -rln "kmpstarter.composeapp.generated.resources" composeApp/src
```

## Removing unused targets

If the user doesn't ship one of the five platforms, remove it cleanly:

1. Delete the target block in `composeApp/build.gradle.kts` (`iosArm64()`, `iosSimulatorArm64()` for "no iOS")
2. Delete the matching source-set folder (`composeApp/src/iosMain/`, `iosMain` test folders if any)
3. For iOS specifically: delete the `iosApp/` directory
4. Delete now-unreferenced libraries in `gradle/libs.versions.toml` if they were target-specific
5. Update [`docs/PLATFORMS.md`](../../docs/PLATFORMS.md) — remove the section for the dropped target
6. Update [`AGENTS.md`](../../AGENTS.md) — adjust the target list mentioned in Project Overview

## Verification

```bash
./gradlew clean
./gradlew :composeApp:assemble                          # Compiles all kept targets
./gradlew :composeApp:allTests                          # Tests pass
./gradlew :composeApp:run                               # Desktop launches with new title
./gradlew :composeApp:assembleDebug                     # Android APK builds (if kept)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun       # Web (if kept)
# iOS: open Xcode and ⌘R against a simulator (if kept)
```

If any target fails, the rebrand isn't done. Common causes:

- Stale `import` for the old generated resources package — search and update
- `mainClass` in `composeApp/build.gradle.kts` doesn't match the renamed Kotlin package
- iOS framework name mismatch — check `iosApp/iosApp/ContentView.swift` `import ComposeApp`

## Documentation pass

After the rebrand:

- [README.md](../../README.md) — new product name, purpose, run commands
- [AGENTS.md](../../AGENTS.md) — Project Overview section
- [docs/PLATFORMS.md](../../docs/PLATFORMS.md) — drop sections for removed targets
- [docs/TECHNOLOGIES.md](../../docs/TECHNOLOGIES.md) — drop unused libraries
- Search for `KMPStarter`, `kmpstarter`, `xergioalex` — replace any remaining references

```bash
grep -rln "KMPStarter\|kmpstarter\|xergioalex" --include="*.md" --include="*.kts" --include="*.kt" --include="*.xml" --include="*.html"
```

## Final commit

```bash
git status
git add .
git commit -m "feat: rebrand starter to <new project name>"
```

If git history is being reset:

```bash
rm -rf .git
git init
git add .
git commit -m "feat: bootstrap from KMPStarter starter as <new name>"
```

## Don't

- Skip the IDE refactor and try to `sed` everything — typos break the build at the worst time
- Change `applicationId` after publishing to Play — you can never change it back
- Rename the iOS framework (`ComposeApp`) unless there's a real reason — it's an internal name
- Forget to update the generated `Res` package imports

## Do

- Confirm every input with the user before editing
- Use the IDE for package renames
- Smoke-test every target the user keeps
- Update every doc that mentions the old name
- Commit once, cleanly, with a conventional message
