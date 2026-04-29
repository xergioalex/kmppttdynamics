# Testing Guide

How to write and run tests in KMPPTTDynamics. The starter ships with `kotlin.test` only — that's enough for shared logic. Add UI testing libraries when you start needing them.

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
./gradlew :composeApp:jvmTest --tests "com.xergioalex.kmppttdynamics.ComposeAppCommonTest.joinCodeIsSixCharsAlphanumeric"
./gradlew :composeApp:jvmTest --continuous     # Watch mode
```

Reports land at `composeApp/build/reports/tests/<task>/index.html`.

## Conventions

1. **Mirror the production package** — `commonTest/kotlin/com/xergioalex/kmppttdynamics/JoinCodeGenerator.kt` becomes `commonTest/kotlin/com/xergioalex/kmppttdynamics/JoinCodeGeneratorTest.kt`
2. **Class name = production class + `Test`** (`JoinCodeGenerator` → `JoinCodeGeneratorTest`)
3. **Method name describes the behavior** — `joinCodeIsSixCharsAlphanumeric`. Keep them in `camelCase` for portability across Native/Wasm runners.
4. **Arrange / Act / Assert** structure with a blank line between sections
5. **One behavior per test method.** If a test name needs "and", split it.
6. **No shared mutable state** between tests — tear down or use fresh instances each test

```kotlin
package com.xergioalex.kmppttdynamics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoinCodeGeneratorTest {

    @Test
    fun joinCodeIsSixCharsAlphanumeric() {
        // Act
        val code = JoinCodeGenerator.generate()

        // Assert
        assertEquals(6, code.length)
        assertTrue(code.all { it.isLetterOrDigit() && (it.isDigit() || it.isUpperCase()) })
    }
}
```

## Current tests (in this repo)

| Suite | What it locks in |
|---|---|
| `ComposeAppCommonTest` | `JoinCodeGenerator` produces 6 alphanumeric chars without confusing glyphs (`0/O/1/I`) and with non-trivial RNG variance |
| `SerializationTest` | The `@EncodeDefault` annotations on every `*Draft` class — guards against the kotlinx.serialization "drop defaults" gotcha that caused our 0 online / Draft polls / Draft raffles bugs. Covers `JoinRequest`, `PollDraft`, `RaffleDraft`, `TriviaQuizDraft`, `TriviaQuestionDraft`, `TriviaChoiceDraft`, `TriviaEntryDraft`, plus the `TriviaStatus` enum lowercase-kebab roundtrip |
| `trivia.TriviaScoringTest` | The Kahoot scoring formula in [`TriviaScoring`](../composeApp/src/commonMain/kotlin/com/xergioalex/kmppttdynamics/trivia/TriviaScoring.kt). Locks the wrong-answer-zero, instant-correct-1000, last-second-500, midpoint-750, late-insert clamp behaviours that mirror the Postgres trigger |
| `trivia.TriviaBoardTest` | The `TriviaBoard.live` getter — the routing rule that decides when the trivia tab takes over the screen |

The trivia tests doubly serve as a parity contract with the Postgres
trigger: any change to either the SQL formula (in
`007_trivia.sql`'s `trivia_compute_answer_score`) or the Kotlin mirror
(`TriviaScoring.kt`) must keep both sides matching.

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

class MeetupRepositoryTest {
    @Test
    fun listsLiveMeetups() = runTest {
        val repo = MeetupRepository(FakeSupabaseClient())
        val live = repo.listLive()
        assertEquals(3, live.size)
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
class HomeScreenTest {
    @Test
    fun homeShowsCreateMeetupButton() = runComposeUiTest {
        val container = AppContainer(settings = AppSettings(MapSettings()))
        setContent { App(container) }
        onNodeWithText("Create meetup").assertIsDisplayed()
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
