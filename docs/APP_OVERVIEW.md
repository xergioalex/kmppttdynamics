# KMPTodoApp — App Overview

A cross-platform Todo app written **once in `commonMain`** and deployed to **Android, iOS, Desktop (JVM), Web (Wasm), and Web (JS)**. The point of this project is to exercise the real bondades of Kotlin Multiplatform — shared domain, shared UI, platform code only where it earns its keep — on a small but realistic product.

> Looking for the runtime story (gradle commands, IDE setup, troubleshooting)? See [Development Commands](DEVELOPMENT_COMMANDS.md), [Running the App](getting-started/RUNNING_THE_APP.md), and [Troubleshooting](getting-started/TROUBLESHOOTING.md).

## What the app does

| Feature | Where it lives | Notes |
|---|---|---|
| **Create / read / update / delete tasks** | `domain/TaskRepository`, `ui/edit/*` | One screen handles both new and existing tasks |
| **Title, notes, category, priority, due date, done** | `domain/Task`, `domain/TaskDraft` | All fields shared across every platform |
| **Priority levels (Low / Medium / High)** | `domain/Priority` | Stored as `INTEGER`; UI uses Material 3 `FilterChip` + colored badges |
| **Due dates with date picker** | `ui/edit/TaskEditScreen` | Material 3 `DatePicker`; persisted as epoch millis |
| **Filter (All / Active / Done) + free-text search** | `ui/list/TaskListViewModel` | Filter persists in app settings; search is in-memory over title/notes/category |
| **Mark done with strikethrough** | `ui/list/TaskListScreen` | Toggling re-sorts: active first, by priority, by due date |
| **Clear completed** | Top bar action | One-shot `DELETE FROM task WHERE is_done = 1` |
| **Categories suggested from existing tasks** | `Tasks.sq` `distinctCategories` | Reactive `Flow<List<String>>` |
| **Theme: System / Light / Dark** | `ui/theme/AppTheme`, `ui/settings/*` | Persisted per-device via `multiplatform-settings` |
| **i18n: English + Spanish** | `composeResources/values{,-es}/` | Picked from system locale |
| **Adaptive layout (single-pane vs list+detail)** | `App.kt` | `BoxWithConstraints` threshold of 720 dp |
| **Share a task** | `platform/TaskSharer` (`expect/actual`) | Native share sheet on Android/iOS, clipboard on Desktop, `navigator.clipboard` on Web |
| **Persistence** | Per-platform — see below | All four "real" targets keep tasks across reboots |

## Persistence per target

`TaskRepository` is a single interface in `commonMain`. Two implementations satisfy it:

| Target | Implementation | Storage backend |
|---|---|---|
| Android | `SqlTaskRepository` (`nonWebMain`) | `AndroidSqliteDriver` → `/data/data/<pkg>/databases/kmptodoapp.db` |
| iOS | `SqlTaskRepository` (`nonWebMain`) | `NativeSqliteDriver` → app sandbox SQLite file |
| Desktop JVM | `SqlTaskRepository` (`nonWebMain`) | `JdbcSqliteDriver` → `~/.kmptodoapp/kmptodoapp.db` |
| Web (JS) | `InMemoryTaskRepository` (`webMain`) | `MutableStateFlow<List<Task>>` — resets on reload |
| Web (Wasm) | `InMemoryTaskRepository` (`webMain`) | Same as JS |

Web persistence is intentionally a follow-up — wiring SQLDelight's `web-worker-driver` requires webpack + `sqljs` setup, and the goal of v1 was to make sure every screen renders and behaves correctly on every target first. The `InMemoryTaskRepository` carries a `FIXME(web-persistence)` marker so the swap is easy to find.

`AppSettings` (theme + filter) follows the same shape but persists everywhere via `multiplatform-settings`:

| Target | Settings backend |
|---|---|
| Android | `SharedPreferencesSettings` |
| iOS | `NSUserDefaultsSettings` |
| Desktop JVM | `PreferencesSettings` (`java.util.prefs`) |
| Web (JS / Wasm) | `StorageSettings` over `localStorage` |

## Source set layout

```
composeApp/src/
├── commonMain/                     ← all UI, ViewModels, domain — shared by every target
│   ├── kotlin/com/xergioalex/kmptodoapp/
│   │   ├── App.kt                  ← single shared root composable + state-based routing
│   │   ├── AppContainer.kt         ← DI-lite holder (TaskRepository, AppSettings, TaskSharer)
│   │   ├── domain/                 ← Task, TaskDraft, Priority, TaskFilter, TaskRepository
│   │   ├── settings/               ← AppSettings + ThemeMode (multiplatform-settings)
│   │   ├── platform/               ← TaskSharer interface + share-text builder
│   │   ├── ui/list, ui/edit, ui/settings, ui/theme
│   │   └── ui/Formatters.kt        ← due-date formatter
│   └── composeResources/values{,-es}/strings.xml
│
├── nonWebMain/                     ← intermediate set seen only by Android / iOS / JVM
│   ├── kotlin/.../data/SqlTaskRepository.kt
│   ├── kotlin/.../data/DatabaseDriverFactory.kt    ← expect class
│   └── sqldelight/com/xergioalex/kmptodoapp/db/Tasks.sq
│
├── androidMain/  ← MainActivity, AndroidTaskSharer, AndroidSqliteDriver actual
├── iosMain/      ← MainViewController, IosTaskSharer, NativeSqliteDriver actual
├── jvmMain/      ← Window { App() }, JvmTaskSharer (clipboard), JdbcSqliteDriver actual
│
├── webMain/      ← shared between JS + Wasm: ComposeViewport entry + InMemoryTaskRepository
├── jsMain/       ← JsTaskSharer + createTaskSharer actual
└── wasmJsMain/   ← WasmTaskSharer + createTaskSharer actual
```

The `nonWebMain` intermediate source set is added manually:

```kotlin
applyDefaultHierarchyTemplate()
sourceSets {
    val nonWebMain by creating { dependsOn(commonMain.get()) }
    androidMain.get().dependsOn(nonWebMain)
    iosMain.get().dependsOn(nonWebMain)
    jvmMain.get().dependsOn(nonWebMain)
}
```

That's how `SqlTaskRepository` is shared across the three "real device" targets without leaking SQLDelight into web builds.

## Architecture in three layers

```
                    ┌──────────────────────────────────┐
   commonMain  →    │  ui/  (Compose screens + VMs)    │  StateFlow + collectAsStateWithLifecycle
                    └──────────────┬───────────────────┘
                                   │ reads / mutates
                                   ▼
                    ┌──────────────────────────────────┐
                    │  domain/  (Task, Repository)     │  Pure Kotlin, zero platform deps
                    └──────────────┬───────────────────┘
                                   │ implements
                                   ▼
   nonWebMain  ┌────────────────────────────────────────┐
   webMain     │  data/  (SqlTaskRepository,            │
               │          InMemoryTaskRepository)        │
               └────────────────┬───────────────────────┘
                                │ uses
                                ▼
   androidMain / iosMain / jvmMain  →  expect/actual DatabaseDriverFactory
```

- **`domain/` is dependency-free Kotlin.** No Android Context, no UIKit, no `Settings`, no SQLDelight types — just `Task`, `Priority`, `TaskFilter`, `TaskDraft`, `TaskRepository`. This makes it trivially testable (no mocks, no DI framework required).
- **`data/` lives one source set higher.** `SqlTaskRepository` only compiles for Android/iOS/JVM (`nonWebMain`). `InMemoryTaskRepository` only compiles for JS/Wasm (`webMain`). Each entry point hands the right one into the `AppContainer`.
- **`ui/` reads from `domain/`.** Screens hold no business logic; ViewModels expose `StateFlow<UiState>` produced by `combine(repository.observeAll(), settings.filter, query)`.

## How the KMP tools are exercised here

| Pattern | Where to look |
|---|---|
| **Shared composables** | `App.kt`, `ui/list`, `ui/edit`, `ui/settings`, `ui/theme` — all in `commonMain`. No per-platform `App` clones. |
| **`expect class` / `actual class`** | `data/DatabaseDriverFactory` (one expect in `nonWebMain`, three actuals: Android/iOS/JVM) |
| **`expect fun` / `actual fun`** | `createTaskSharer()` in `webMain`, with JS + Wasm actuals |
| **Interface + per-platform impl** | `platform/TaskSharer` — five concrete implementations using each platform's native share API |
| **Custom source set hierarchy** | The `nonWebMain` group, manually wired so Android/iOS/JVM share `SqlTaskRepository` without dragging it into web |
| **Compose Multiplatform resources** | `composeResources/values/strings.xml` + `values-es/strings.xml`; consumed via `Res.string.*` from the generated `kmptodoapp.composeapp.generated.resources` package |
| **Reactive data layer** | `Tasks.sq` queries → SQLDelight `Query.asFlow()` → `mapToList` → repository `Flow<List<Task>>` → `combine` in the ViewModel |
| **One-shot navigation events** | `TaskEditViewModel` exposes a `Channel<TaskEditEffect>` for `Saved`/`Deleted`/`NotFound` — UI state holds form fields only, so cached ViewModels never replay stale "saved" flags |
| **`androidx.lifecycle` ViewModels in common code** | `TaskListViewModel`, `TaskEditViewModel`, `SettingsViewModel` all extend the multiplatform `ViewModel` |
| **Multiplatform `kotlin.time`** | Domain uses `kotlin.time.Instant` and `kotlin.time.Clock` (the future-proof stdlib types); kotlinx-datetime supplies only `LocalDateTime` + `TimeZone` for formatting |

## DI-lite via `AppContainer`

There's no Koin / Kodein / Dagger — just a small data class:

```kotlin
data class AppContainer(
    val tasks: TaskRepository,
    val settings: AppSettings,
    val sharer: TaskSharer,
)
```

Each entry point builds its own `AppContainer` with the implementations that fit its target, then hands it to `App(container)`. Composables read it as a parameter. Cheap, explicit, refactor-friendly.

```kotlin
// Desktop entry point
val container = AppContainer(
    tasks = SqlTaskRepository(DatabaseDriverFactory()),
    settings = AppSettings(PreferencesSettings(Preferences.userRoot().node("com/xergioalex/kmptodoapp"))),
    sharer = JvmTaskSharer(),
)
application { Window(...) { App(container) } }
```

```kotlin
// Web entry point
val container = AppContainer(
    tasks = InMemoryTaskRepository(),
    settings = AppSettings(StorageSettings()),
    sharer = createTaskSharer(),
)
ComposeViewport { App(container) }
```

When the app grows enough to justify a real DI framework, the swap is local to those four entry points.

## Build & run

| Target | Command |
|---|---|
| Desktop (with Compose Hot Reload) | `./gradlew :composeApp:run` |
| Android (debug install) | `./gradlew :composeApp:installDebug` |
| iOS | open `iosApp/iosApp.xcodeproj` in Xcode → ⌘R |
| Web (Wasm, recommended) | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| Web (JS, fallback) | `./gradlew :composeApp:jsBrowserDevelopmentRun` |
| All Kotlin compiles | `./gradlew :composeApp:assemble` |
| JVM tests | `./gradlew :composeApp:jvmTest` |

> Use Java 21 — `export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"` on macOS. The bundled Kotlin compiler in Gradle 8.14 doesn't recognize Java 26 yet.

## What's next

Hooks intentionally left for follow-ups:

- **Web persistence**: swap `InMemoryTaskRepository` for SQLDelight's `web-worker-driver` (needs webpack + `sqljs` resource setup).
- **Local reminders**: `expect/actual` over `AlarmManager` (Android) / `UNUserNotificationCenter` (iOS) / `java.util.Timer` or system tray (Desktop). The `dueAt` field is already in the domain.
- **Sub-tasks**: extend the schema with a self-referencing `parent_task_id` plus a nested checklist UI.
- **Drag-to-reorder** on the list, persisting an `order_index`.
- **Tests** in `commonTest` for `TaskListViewModel` (filter + search) — the logic is pure and trivially exercisable.
- **Common ViewModel tests** with `kotlinx-coroutines-test`.

None of these need cross-platform plumbing — they're product decisions waiting for a product owner.
