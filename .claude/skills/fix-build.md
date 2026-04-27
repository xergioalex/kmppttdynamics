---
name: fix-build
description: Diagnose and repair a failing Gradle build
---

# Skill: `/fix-build`

The build is broken. Find the root cause and fix it without disabling the failing check.

## Inputs to confirm

- **Which task fails?** `./gradlew :composeApp:assemble`, `:composeApp:jvmTest`, etc.
- **Full error output** — paste the last ~50 lines, especially the `* What went wrong:` block

## Procedure

### 1. Reproduce with `--stacktrace`

```bash
./gradlew :composeApp:<task> --stacktrace
```

Read the actual root cause, not just the surface error. Gradle's `Caused by:` chain matters.

### 2. Classify the failure

| Symptom | Likely cause | Section below |
|---|---|---|
| `expected actual is missing` | Missing `actual` for some target | A |
| `Unresolved reference: <Res|generated.resources>` | Compose Resources didn't regenerate | B |
| `Could not find <library>` | Dependency not in repo / wrong coordinate | C |
| `Could not resolve <plugin>` | Plugin / Kotlin version mismatch | D |
| `Module was compiled with an incompatible version of Kotlin` | Kotlin / Compose Compiler version mismatch | E |
| `Duplicate class` / `Duplicate JAR entry` | Conflicting transitive deps | F |
| `Out of memory` / `Java heap space` | JVM heap too small | G |
| `R8 / minify` errors | ProGuard rules missing for a library | H |
| `linkDebugFrameworkIosX` fails | Native compilation issue | I |
| `Build file 'build.gradle.kts' line N` | Kotlin syntax / DSL error | J |

### A. Missing `actual`

The error names the `expect` and the source set without an `actual`. Add the `actual` in that source set following [`/add-expect-actual`](add-expect-actual.md). Don't comment out the `expect` to silence the build.

### B. Compose Resources not regenerated

```bash
./gradlew clean :composeApp:assemble
```

If it persists, check that the file is actually under `composeApp/src/commonMain/composeResources/<type>/`, the filename uses `snake_case`, and the XML key is a valid Kotlin identifier.

### C. Could not find library

1. Verify the coordinate in `gradle/libs.versions.toml` matches the published artifact (Maven Central, Google Maven, JetBrains)
2. Verify the version exists for the multiplatform variant you need (some libraries publish JVM-only)
3. Try `--refresh-dependencies` once: `./gradlew :composeApp:assemble --refresh-dependencies`
4. If the library is hosted on a custom repo, add it in `settings.gradle.kts` under `dependencyResolutionManagement.repositories`

### D. Plugin resolution failure

Plugin versions go in `[plugins]` of `gradle/libs.versions.toml`. Common causes:

- Plugin requires a newer Gradle version → update the wrapper
- Plugin requires a specific Kotlin version → check the plugin's docs and bump Kotlin in the catalog

### E. Kotlin / Compose Compiler mismatch

Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is **version-tied** to Kotlin. Check the [Compose Multiplatform compatibility table](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html). Bump both in lockstep:

```toml
[versions]
kotlin = "2.3.20"
composeMultiplatform = "1.10.3"   # check compatibility
```

### F. Duplicate class / JAR entry

A transitive dependency got pulled in twice with different versions. Diagnose:

```bash
./gradlew :composeApp:dependencies | grep <library-fragment>
```

Resolve by:

1. Excluding the transitive: `implementation(libs.foo) { exclude(group = "...", module = "...") }`
2. Forcing a version in the catalog
3. Deleting the redundant direct declaration

### G. Out of memory

Bump heap in `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx6144M -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx4096M
```

If a Kotlin/Native compile is OOMing on CI, also pass `-Xmx` to the linker:

```kotlin
kotlin {
    iosArm64 {
        binaries.framework {
            linkerOpts.add("-Xmx4G")
        }
    }
}
```

### H. R8 / minify errors

The release build with `isMinifyEnabled = true` strips classes that are referenced via reflection. Add ProGuard rules for the affected library to `composeApp/proguard-rules.pro`.

Common offenders: kotlinx-serialization, Ktor, anything with `@Serializable`. Their docs ship recommended rules — copy them.

### I. iOS native link failure

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --stacktrace
```

Common causes:

- Symbol collision between two static frameworks → ensure only one Compose-MP framework is linked
- Wrong CocoaPods integration → if you adopted CocoaPods, check `iosApp/Podfile` and run `pod install`
- Apple Silicon vs Intel — `iosSimulatorArm64` only runs on Apple Silicon Macs; add `iosX64` for Intel

### J. Kotlin DSL error in `build.gradle.kts`

Read the line number and column. Common mistakes:

- Missing comma in a list
- Wrong API for the version (read the deprecation message — Kotlin DSL evolves)
- `apply false` in the wrong block
- Mixing `kotlin {` (KMP DSL) and `android {` (AGP DSL) at wrong nesting

### 3. After fixing

```bash
./gradlew clean
./gradlew :composeApp:assemble :composeApp:allTests
```

If it passes, smoke-test the platform you broke (Desktop, Android, iOS, Web).

### 4. Don't bypass

Avoid:

- `--rerun-tasks` to "force" a flaky build (find the flake's root cause)
- Disabling configuration cache for one bad spec (fix the spec)
- Adding `// noinspection` or `@Suppress` to silence a real warning
- Lowering `compileSdk` / `kotlin.code.style` to make the build pass — that's hiding the bug

### 5. After repeated fixes

If you fix the same kind of build break twice, write it up:

- A note in [`docs/DEVELOPMENT_COMMANDS.md`](../../docs/DEVELOPMENT_COMMANDS.md) under "Reset a stuck build" or a new section
- A defensive check in `build.gradle.kts` if applicable

## Don't

- Disable a check to make the build pass (fixes hide in plain sight)
- Bump every dependency simultaneously — bump one, build, then the next
- `git checkout -- .` to "reset" partially completed work — investigate first

## Do

- Read `Caused by:` chains to find the root cause
- Use `--stacktrace` and `--info` liberally
- Check `~/.gradle/caches` deletion as a *last* resort, after all else fails
