# Testing Guide

How to write and run tests in KMPStarter. The starter ships with `kotlin.test` only — that's enough for shared logic. Add UI testing libraries when you start needing them.

## Where tests live

| Source set | Compiled for | Use |
|---|---|---|
| `composeApp/src/commonTest/` | every test-capable target | Logic that lives in `commonMain` (recommended default) |
| `composeApp/src/jvmTest/` | JVM only | Tests that need Java/Swing APIs |
| `composeApp/src/androidUnitTest/` | Android unit (JVM, Robolectric-style) | Android-specific actuals, manifest-aware tests |
| `composeApp/src/androidInstrumentedTest/` | Android instrumented | Tests that need a device/emulator (UI tests) |
| `composeApp/src/iosTest/` | iOS targets | iOS-specific actuals or UIKit-touching code |
| `composeApp/src/jsTest/`, `wasmJsTest/` | JS / Wasm browser runner | Browser-specific actuals |

The starter currently has only `commonTest`. Add the others on demand — the source set hierarchy is automatic, you just need to create the directory.

## Running tests

```bash
./gradlew :composeApp:allTests                 # Everything
./gradlew :composeApp:jvmTest                  # Fastest — recommended default
./gradlew :composeApp:testDebugUnitTest        # Android unit
./gradlew :composeApp:iosSimulatorArm64Test    # iOS simulator
./gradlew :composeApp:jvmTest --tests "com.xergioalex.kmpstarter.GreetingTest.greetsCurrentPlatform"
./gradlew :composeApp:jvmTest --continuous     # Watch mode
```

Reports land at `composeApp/build/reports/tests/<task>/index.html`.

## Conventions

1. **Mirror the production package** — `commonTest/kotlin/com/xergioalex/kmpstarter/Greeting.kt` becomes `commonTest/kotlin/com/xergioalex/kmpstarter/GreetingTest.kt`
2. **Class name = production class + `Test`** (`Greeting` → `GreetingTest`)
3. **Method name describes the behavior** — `returnsHelloWithPlatformName`. Keep them in `camelCase` for portability across Native/Wasm runners.
4. **Arrange / Act / Assert** structure with a blank line between sections
5. **One behavior per test method.** If a test name needs "and", split it.
6. **No shared mutable state** between tests — tear down or use fresh instances each test

```kotlin
package com.xergioalex.kmpstarter

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {

    @Test
    fun returnsHelloWithPlatformName() {
        // Arrange
        val greeting = Greeting()

        // Act
        val result = greeting.greet()

        // Assert
        assertEquals("Hello, ${getPlatform().name}!", result)
    }
}
```

## What `kotlin.test` gives you

```kotlin
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertContains
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.fail
```

That covers most logic tests. For coroutine code, add `kotlinx-coroutines-test` to `commonTest.dependencies` and use `runTest { ... }`.

## Suspending / coroutine tests

Add to the catalog and to `commonTest.dependencies`:

```toml
# gradle/libs.versions.toml
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

```kotlin
// commonTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskRepositoryTest {
    @Test
    fun loadsTasks() = runTest {
        val repo = TaskRepository(FakeApi())
        val tasks = repo.list()
        assertEquals(3, tasks.size)
    }
}
```

`runTest` provides a `TestDispatcher` so virtual time advances instantly — your tests don't actually wait for delays.

## Compose UI tests (when you need them)

Compose Multiplatform offers `compose-ui-test-junit4` (JVM/Android) and `compose-ui-test` for non-JVM targets. The starter doesn't wire them — when you add UI tests, declare:

```kotlin
commonTest.dependencies {
    implementation(libs.compose.ui.test)              // multiplatform
}
jvmTest.dependencies {
    implementation(compose.desktop.uiTestJUnit4)
}
```

Then write tests with `runComposeUiTest` (multiplatform) or `createComposeRule()` (JVM/Android):

```kotlin
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {
    @Test
    fun appShowsButton() = runComposeUiTest {
        setContent { App() }
        onNodeWithText("Click me!").assertIsDisplayed()
    }
}
```

Document the addition in [Technologies](TECHNOLOGIES.md).

## Mocking

The starter has no mocking framework. For multiplatform code, prefer **fakes** (hand-rolled implementations) over mocks — they work in every target and survive refactors better.

If you must mock, MockK is JVM-only; for KMP use `mockative` or pass interfaces and write fakes.

## What to test

**Always:**

- Pure logic in `commonMain` (mappers, validators, formatters, state reducers)
- ViewModels — their public state should be deterministic for a given input
- `expect`/`actual` boundaries — at minimum a smoke test per platform that the `actual` doesn't throw

**Sometimes:**

- Composable layouts — only when the layout is non-trivial; UI tests are expensive
- Repositories — with a fake data source

**Rarely:**

- Pure Material 3 wrappers without state — they break on every Material upgrade and rarely catch real bugs

## Coverage

No coverage tool is wired. To add Kover (multiplatform-friendly):

```kotlin
// composeApp/build.gradle.kts
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.9.0"
}
```

Then `./gradlew koverHtmlReport`. Update [Technologies](TECHNOLOGIES.md) and [Development Commands](DEVELOPMENT_COMMANDS.md) when you add it.

## Speed tips

- Keep `:composeApp:jvmTest` as your inner loop — it's typically <1s per change after the first run
- Use `--continuous` to re-run on save
- Don't run `:composeApp:allTests` on every save — reserve it for pre-push
- iOS test tasks are slow because they boot a simulator; only run them when touching `iosMain`

## Pre-push checklist

```bash
./gradlew :composeApp:assemble :composeApp:allTests
```

If both pass, you can push. Configure CI to run the same.
