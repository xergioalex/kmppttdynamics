---
name: write-tests
description: Author tests in commonTest (or platform-specific) for the current change
---

# Skill: `/write-tests`

Add tests for code that just changed or that lacks coverage. Default to `commonTest` and only descend into platform-specific test source sets when the code under test is platform-specific.

## When to use

- The user just shipped untested code and wants tests
- A bug was found and needs a regression test
- The user asks "write tests for X"

## Decide where the test lives

| Code under test lives in | Test goes in |
|---|---|
| `commonMain` | `commonTest` (runs on every test-capable target) |
| `androidMain` (only) | `androidUnitTest` |
| `iosMain` (only) | `iosTest` |
| `jvmMain` (only) | `jvmTest` |
| `jsMain` / `wasmJsMain` | `jsTest` / `wasmJsTest` |
| Hybrid (an `expect` + `actual`s) | `commonTest` for the contract, platform-specific tests for `actual` quirks |

## Procedure

### 1. Read the code under test

Don't write tests blindly. Read the function / class to understand:

- What inputs are valid?
- What edge cases exist (empty, null, boundary values)?
- What side effects occur?

### 2. Pick the test framework

Default: `kotlin.test` (already wired). For coroutines, add `kotlinx-coroutines-test` to `commonTest.dependencies` and the catalog. For Compose UI tests, add `compose-ui-test` (multiplatform) and `compose.desktop.uiTestJUnit4` (JVM helper).

### 3. Mirror the package

`commonMain/kotlin/com/xergioalex/kmpstarter/tasks/Task.kt` →
`commonTest/kotlin/com/xergioalex/kmpstarter/tasks/TaskTest.kt`

### 4. Write tests following the AAA pattern

```kotlin
package com.xergioalex.kmpstarter.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskTest {

    @Test
    fun marksTaskAsDone() {
        // Arrange
        val task = Task(id = "1", title = "Buy milk", done = false)

        // Act
        val result = task.copy(done = true)

        // Assert
        assertEquals(true, result.done)
    }

    @Test
    fun rejectsBlankTitle() {
        assertFailsWith<IllegalArgumentException> {
            Task(id = "1", title = "", done = false)
        }
    }
}
```

Rules:

- One behavior per test method
- Method name in `camelCase`, describes the behavior (`marksTaskAsDone`, `rejectsBlankTitle`)
- Arrange / Act / Assert — separate sections with blank lines
- No shared mutable state between tests
- Avoid backticks in `commonTest` (Native/Wasm runners reject them)

### 5. Coroutine tests

Add to the catalog if not present:

```toml
# gradle/libs.versions.toml
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

```kotlin
commonTest.dependencies {
    implementation(libs.kotlin.test)
    implementation(libs.kotlinx.coroutines.test)
}
```

Use `runTest`:

```kotlin
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

### 6. Test ViewModels

```kotlin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskListViewModelTest {

    @Test
    fun startsLoading() {
        val vm = TaskListViewModel()
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun loadsTasksOnStart() = runTest {
        val vm = TaskListViewModel(FakeRepo(tasks = listOf("a", "b")))
        // advance time / collect once if needed
        assertEquals(listOf("a", "b"), vm.state.value.tasks)
    }
}
```

If the ViewModel uses `viewModelScope`, override the `Main` dispatcher in tests with a `TestDispatcher` (see `kotlinx-coroutines-test` docs).

### 7. Use fakes, not mocks

Multiplatform-friendly: hand-roll a `FakeApi` that implements the same interface. It works on every target and survives refactors better than mock libraries (most of which are JVM-only).

```kotlin
class FakeTaskApi(
    private val tasks: List<Task> = emptyList(),
) : TaskApi {
    override suspend fun list(): List<Task> = tasks
}
```

### 8. Run

```bash
./gradlew :composeApp:jvmTest --tests "com.xergioalex.kmpstarter.tasks.TaskListViewModelTest"
```

Or watch mode:

```bash
./gradlew :composeApp:jvmTest --continuous
```

For `commonTest`, JVM is the fastest target. Run `:composeApp:allTests` before pushing.

### 9. What to test

**Always:**

- Pure logic (mappers, validators, formatters)
- ViewModels — their public state should be deterministic
- `expect`/`actual` boundaries — at least a smoke test per platform

**Sometimes:**

- Composable layouts — only when non-trivial
- Repositories — with a fake data source

**Rarely:**

- Pure Material 3 wrappers without state — they break on every Material upgrade and rarely catch real bugs

## Compose UI tests (when needed)

Multiplatform UI tests use `runComposeUiTest`:

```kotlin
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TaskListScreenTest {
    @Test
    fun showsHeader() = runComposeUiTest {
        setContent { TaskListScreen() }
        onNodeWithText("Tasks").assertIsDisplayed()
    }
}
```

Add the deps in the catalog and `commonTest.dependencies`. Document the addition in `docs/TECHNOLOGIES.md`.

## Pitfalls

1. **Backticked test names in `commonTest`** — fail on Native/Wasm runners. Use camelCase
2. **Shared static state** — `companion object` mutables leak between tests. Use fresh instances per test
3. **`runBlocking` instead of `runTest`** — `runBlocking` actually waits for delays; `runTest` advances virtual time instantly
4. **Testing implementation details** — assert public behavior (state value, return value), not internal helpers

## Don't

- Skip the test because "it's obvious" — bugs hide in obvious code
- Use mocking libraries in `commonTest` (most are JVM-only); use fakes
- Test composables when a ViewModel test would catch the same bug

## Do

- Mirror the production package
- Class name = production class + `Test`
- One behavior per test
- Update `docs/TESTING_GUIDE.md` if you adopt a new testing tool
