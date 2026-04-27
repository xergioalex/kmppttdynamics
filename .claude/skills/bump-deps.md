---
name: bump-deps
description: Update gradle/libs.versions.toml safely with verification per platform
---

# Skill: `/bump-deps`

Bump one or more dependencies in `gradle/libs.versions.toml`, then verify every platform still compiles and tests pass.

## When to use

- The user asks to "bump X to version Y"
- Dependabot / Renovate / a security advisory flags an outdated dep
- A new feature requires a newer version

## Inputs to confirm

- **Which library / plugin / Kotlin / AGP / Compose Multiplatform** is being bumped
- **Target version** — exact version string
- **Why** — feature, security, hygiene

## Pre-flight

For Kotlin / Compose Multiplatform / Compose Compiler / AGP, verify compatibility **before** editing:

- [Compose Multiplatform compatibility table](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html)
- [AGP compatibility with Kotlin](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin)
- The library's release notes for breaking changes

## Procedure

### 1. Edit only `gradle/libs.versions.toml`

Bump the version under `[versions]`. **Don't** edit `composeApp/build.gradle.kts` to inline a literal version string.

```toml
[versions]
composeMultiplatform = "1.10.3"   # was 1.10.2
```

If a single bump touches multiple libraries (e.g., bumping Kotlin requires bumping the Compose Compiler plugin), update all of them.

### 2. Bump one library at a time

Multi-bumps mask which dependency broke a build. Even if Renovate proposes ten in one PR, take them one at a time locally.

### 3. Sync and assemble

```bash
./gradlew --stop
./gradlew clean
./gradlew :composeApp:assemble
```

Reading the error matters. If it's:

- `Could not find <coord>` → wrong coordinate or version
- `Unresolved reference` → API removed or renamed in the new version (read the release notes)
- `expected actual is missing` → unrelated; pre-existing issue
- A deprecation warning → don't ignore; address in this same change if straightforward

### 4. Run all tests

```bash
./gradlew :composeApp:allTests
```

For Kotlin / Compose / AGP bumps:

```bash
./gradlew :composeApp:run                                # Desktop
./gradlew :composeApp:installDebug                       # Android
./gradlew :composeApp:wasmJsBrowserDevelopmentRun        # Web
# iOS: open Xcode and ⌘R against a simulator
```

Any platform that fails to launch is a regression — investigate before continuing.

### 5. Address breaking changes

If the new version removed an API:

- Find the replacement in the release notes
- Update call sites
- Re-run tests

If the new version changed default behavior subtly (e.g., a Compose modifier order change), audit related code paths.

### 6. Update docs

If the bump:

- Adds or renames a public API used in the docs → update [`docs/TECHNOLOGIES.md`](../../docs/TECHNOLOGIES.md) and any other affected doc
- Includes a perf-relevant change → update [`docs/PERFORMANCE.md`](../../docs/PERFORMANCE.md)
- Affects a security advisory → update [`docs/SECURITY.md`](../../docs/SECURITY.md)

### 7. Commit

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts <other-affected-files>
git commit -m "chore: bump <library> to <version>"
```

Conventional commit type:

- `chore` — routine bump
- `fix` — security or bug-fix bump
- `feat` — bumping unlocks a new feature you're using
- `build` — toolchain (Kotlin, AGP, Gradle wrapper)

## Special cases

### Kotlin

Kotlin and the Compose Compiler plugin must match. Check the compatibility table, then:

```toml
[versions]
kotlin = "2.3.20"
# composeCompiler is bound to kotlin via:
# composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

After the bump, `./gradlew --stop` is mandatory before re-assembling — Kotlin daemons cache classpath state that becomes invalid.

### AGP

Bumping AGP often requires bumping `compileSdk`. Read the [AGP release notes](https://developer.android.com/build/releases/gradle-plugin) for required Gradle and JDK versions. Then:

```toml
[versions]
agp = "8.11.2"
android-compileSdk = "36"
```

### Gradle wrapper

```bash
./gradlew wrapper --gradle-version=<version>
```

This updates `gradle/wrapper/gradle-wrapper.properties`. Commit both the properties file and the regenerated `gradle-wrapper.jar`. Don't edit them by hand.

### Major bump (1.x → 2.x)

For libraries with a major version bump:

1. Read the migration guide
2. Stage the bump on a separate branch
3. Run `./gradlew :composeApp:assemble :composeApp:allTests` after each fix to track progress
4. Smoke-test all five platforms before merging

## Don't

- Bump multiple unrelated libraries in one commit
- Ignore deprecation warnings introduced by the bump
- Inline a version in `build.gradle.kts` to "test it real quick"
- Skip the compatibility table check for Kotlin / Compose / AGP bumps

## Do

- Read release notes (at minimum the headline changes)
- Run all five platforms after a major bump (Kotlin / Compose / AGP)
- Update docs in the same commit
- Use conventional commit messages

## Verification checklist

- [ ] Edited only `gradle/libs.versions.toml` (and `gradle-wrapper.properties` for Gradle bumps)
- [ ] `./gradlew :composeApp:assemble` succeeds
- [ ] `./gradlew :composeApp:allTests` passes
- [ ] Smoke-tested affected platforms (or all five for Kotlin/Compose/AGP bumps)
- [ ] Deprecation warnings introduced by the bump are addressed or documented
- [ ] Docs updated if the bump affects documented APIs / behavior
- [ ] Conventional commit message
