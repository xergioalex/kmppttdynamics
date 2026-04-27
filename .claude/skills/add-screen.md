---
name: add-screen
description: Add a new shared Compose screen wired into App() with state, strings, and a basic test
---

# Skill: `/add-screen`

Add a new screen as a `@Composable` in `commonMain`, wire it into `App()`, route through translations, and stub a test.

## When to use

The user asks to "add a screen", "add a page", or "create a new feature view". This is the canonical path for any new screen in the app.

## Inputs to confirm with the user

- **Screen name** (e.g., `TaskList`, `Settings`)
- **What it shows** — a sentence or two of intent
- **Does it need a `ViewModel`?** Default yes for any screen with non-trivial state
- **How is it reached** — replaces `App()` content, navigated to from another screen, gated by a state flag

## Procedure

### 1. Pick the package

Single root: `com.xergioalex.kmptodoapp`. Sub-package by feature, not layer:

```
commonMain/kotlin/com/xergioalex/kmptodoapp/<feature>/
    <Feature>Screen.kt        # @Composable, the screen
    <Feature>ViewModel.kt     # Optional but default
    <Feature>UiState.kt       # Optional, only if state is non-trivial
```

For a `TaskList` screen → package `com.xergioalex.kmptodoapp.tasks`.

### 2. Create the ViewModel (if needed)

```kotlin
// commonMain/kotlin/com/xergioalex/kmptodoapp/tasks/TaskListViewModel.kt
package com.xergioalex.kmptodoapp.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskListViewModel : ViewModel() {

    private val _state = MutableStateFlow(TaskListUiState())
    val state: StateFlow<TaskListUiState> = _state.asStateFlow()

    // Add intents here, e.g. fun onTaskToggled(id: String) { ... }
}

data class TaskListUiState(
    val tasks: List<String> = emptyList(),
    val isLoading: Boolean = false,
)
```

If the ViewModel needs coroutines, the `kotlinx-coroutines-core` import comes through Compose. If you start writing meaningful coroutine logic, add an explicit dependency in `gradle/libs.versions.toml` and document it.

### 3. Create the screen composable

```kotlin
// commonMain/kotlin/com/xergioalex/kmptodoapp/tasks/TaskListScreen.kt
package com.xergioalex.kmptodoapp.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.task_list_title

@Composable
fun TaskListScreen(
    modifier: Modifier = Modifier,
    viewModel: TaskListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(Res.string.task_list_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        LazyColumn {
            items(state.tasks, key = { it }) { task ->
                Text(task, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
```

Rules:

- `modifier: Modifier = Modifier` is the first non-required parameter
- Reads via `collectAsStateWithLifecycle()` — never `.collectAsState()` (no lifecycle awareness)
- All user-visible strings via `stringResource(...)` — never hardcoded
- Lists use `LazyColumn` with a `key`
- Material 3 typography from `MaterialTheme.typography.*`

### 4. Add the strings

Edit `composeApp/src/commonMain/composeResources/values/strings.xml`:

```xml
<string name="task_list_title">Tasks</string>
```

If you have other locales (`values-es/`, etc.), add the translated entries to each.

### 5. Wire into `App()`

```kotlin
// commonMain/kotlin/com/xergioalex/kmptodoapp/App.kt
@Composable
@Preview
fun App() {
    MaterialTheme {
        // If this is now the main screen:
        TaskListScreen()

        // If gated by state, route here based on a Navigation library or a simple
        // when(currentScreen) { ... } once you adopt one. Don't re-introduce the
        // bootstrap "Click me" button as a permanent UI; remove it when you have
        // a real screen.
    }
}
```

If the project already has a navigation system, add the screen to the route table; do **not** introduce a new entry point in any platform source set.

### 6. Add a test

```kotlin
// commonTest/kotlin/com/xergioalex/kmptodoapp/tasks/TaskListViewModelTest.kt
package com.xergioalex.kmptodoapp.tasks

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskListViewModelTest {
    @Test
    fun startsWithEmptyTasks() {
        val vm = TaskListViewModel()
        assertEquals(emptyList(), vm.state.value.tasks)
    }
}
```

### 7. Verify

```bash
./gradlew :composeApp:assemble                      # Compiles for all targets
./gradlew :composeApp:jvmTest                       # Tests pass
./gradlew :composeApp:run                           # Smoke-test the screen on Desktop
```

If the user has the device available, also smoke-test Android (`./gradlew :composeApp:installDebug`) and iOS (Xcode ⌘R).

### 8. Documentation

If the screen introduces a new pattern (navigation library, dependency injection, etc.), update [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md) and [AGENTS.md](../../AGENTS.md) accordingly.

## Don't

- Hardcode user-visible strings — always `stringResource(...)`
- Read `viewModel.state.value` directly inside a composable — always `collectAsStateWithLifecycle()`
- Put screen logic in the composable; it belongs in the ViewModel
- Add a platform-specific screen — screens live in `commonMain`
- Skip the test — even a smoke test for the initial state catches regressions

## Do

- Keep the composable stateless except for `viewModel()`
- Hoist state up; pass values down; pass events up via lambdas
- Wrap the screen in `MaterialTheme` only at the App root, not per-screen
- Use `LazyColumn` for any list, even if today there are 3 items
