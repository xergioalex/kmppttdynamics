# Standards

Canonical coding rules for KMPPTTDynamics. Every contributor (human or agent) must follow these. The IDE's Kotlin formatter (`kotlin.code.style=official`) handles most details — these standards cover the things the formatter cannot decide for you.

## Language

- **English only** for code, identifiers, comments, KDoc, commit messages, branch names, and PR descriptions
- User-visible strings live in `commonMain/composeResources/values*/strings.xml` and are read with `stringResource(Res.string.*)` — see [I18N Guide](I18N_GUIDE.md)

## Naming

| Element | Convention | Example |
|---|---|---|
| Package | `lowercase.dotted` | `com.xergioalex.kmppttdynamics.meetups` |
| File | Matches main declaration, `PascalCase.kt` | `MeetupRepository.kt` |
| Platform `actual` file | `Foo.<platform>.kt` | `Platform.android.kt` |
| Class / interface / object | `PascalCase` | `MeetupRepository` |
| Composable function | `PascalCase` (treat like a class) | `RoomScreen()` |
| Top-level function (non-composable) | `camelCase` | `formatRelativeTime()` |
| Constant (compile-time) | `SCREAMING_SNAKE_CASE` | `const val MAX_TITLE_LENGTH = 120` |
| Test method | `camelCase` describing behavior, no backticks unless the test runner is JVM-only | `joinCodeIsSixCharsAlphanumeric` |

When the test target is JVM/Android only, backtick-style names are fine: `` `returns empty list when no tasks` ``. Avoid them in `commonTest` because some Kotlin/Native and Wasm runners reject backticked identifiers.

## Package layout

Single root package: `com.xergioalex.kmppttdynamics`. Subpackages by **feature**, not by layer:

```
com.xergioalex.kmppttdynamics
├── domain/                # Cross-cutting models (Meetup, MeetupParticipant, …)
├── supabase/              # SupabaseClientProvider
├── meetups/               # MeetupRepository
├── participants/          # ParticipantRepository
├── settings/              # AppSettings (theme, last display name)
└── ui/                    # home, create, join, room, theme, components
```

Avoid generic top-level buckets like `models/`, `utils/`, `viewmodels/` — they rot fast.

## expect/actual

1. **`expect` declarations live in `commonMain` only.** Never declare `expect` in a platform source set.
2. **Provide an `actual` for every active target.** A missing `actual` only fails the build for that target — easy to miss until release.
3. **Filename:** `Foo.<platform>.kt` for the file containing the `actual` (e.g., `Clock.android.kt`). The `platform` token matches the source set: `android`, `ios`, `jvm`, `js`, `wasmJs`.
4. **Smallest possible surface.** Prefer `expect fun nowMillis(): Long` over `expect class Clock { ... }` with five methods. Each `expect` is a maintenance tax × number of targets.
5. **Prefer multiplatform libraries over hand-rolled `expect`.** Need a clock? Use `kotlinx-datetime`. Need IO? Use `okio` or `kotlinx-io`. Need a network client? Use Ktor. Custom `expect`/`actual` is a last resort.
6. **Never declare `actual` in `commonMain`.** That's a Kotlin compiler error and confuses tools.

```kotlin
// commonMain/Platform.kt
interface Platform {
    val name: String
}
expect fun getPlatform(): Platform
```

```kotlin
// androidMain/Platform.android.kt
class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}
actual fun getPlatform(): Platform = AndroidPlatform()
```

(For settings persistence we use the `multiplatform-settings` library, which already ships per-platform `actual`s — exactly the kind of off-the-shelf abstraction `expect`/`actual` should defer to.)

## Composables

1. **Stateless when possible.** A composable that takes data and callbacks recomposes predictably and is testable without a host.
2. **Hoist state.** Put `mutableStateOf`/`StateFlow` in a `ViewModel` (or higher up the call tree) and pass values down.
3. **Stable parameters.** Prefer `data class`/`@Immutable` types for parameters so Compose can skip recomposition.
4. **`Modifier` first.** Composables that render UI accept `modifier: Modifier = Modifier` as their **first non-required parameter** so callers can inject layout constraints.
5. **No business logic in composables.** Compute in a `ViewModel` or a pure function; the composable only describes UI.
6. **`@Composable` previews** annotate with `@Preview` from `androidx.compose.ui.tooling.preview` — multiplatform safe.

```kotlin
@Composable
fun ParticipantRow(
    participant: MeetupParticipant,
    isMe: Boolean,
    modifier: Modifier = Modifier,
) {
    // ... no fetching, no IO, no side effects beyond LaunchedEffect when needed
}
```

## Modifiers

- Order matters. Apply `padding` before `background` to avoid coloring the padding; apply `clip` before `background` so the background respects the clip.
- Prefer `start`/`end` over `left`/`right` to support RTL.
- Use `Modifier.testTag("foo")` for UI tests; namespace tags with the screen (`task-list:add-button`).

## State and side effects

- **Prefer `StateFlow`** in ViewModels for screen state; collect with `collectAsStateWithLifecycle()` from `androidx.lifecycle:lifecycle-runtime-compose`.
- **Use `LaunchedEffect`** for one-shot or key-driven side effects.
- **Use `DisposableEffect`** when subscribing to a resource that must be released.
- **Never** call suspending functions directly from a composable body — they only run inside `LaunchedEffect` / `rememberCoroutineScope`.

## Imports

`kotlin.code.style=official` is alphabetical with no manual grouping and no wildcards. Let the IDE do it. Do **not** insert blank lines between import groups.

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.xergioalex.kmppttdynamics.core.theme.AppTheme
```

If your IDE reformats imports differently, your IDE has the wrong style — set "Code Style: Kotlin → Set from… → Kotlin style guide".

## Visibility

- Default to `internal` for module-internal helpers — keeps the public API surface small
- `private` for helpers used only in the same file
- `public` (the default) only for things that need to be called from another module or from `iosApp/`

## Null safety

- Avoid `!!` outside of test code. If you need it, leave a comment explaining the invariant.
- Prefer `?.let { }`, `?:`, and `requireNotNull(value) { "context" }` over `!!`.
- Use `Result<T>` (or a sealed class) to represent failures from network/IO instead of throwing through `commonMain`.

## Coroutines

- Use `viewModelScope` (from `lifecycle-viewmodel-compose`) for screen-scoped work.
- Don't write `GlobalScope.launch` — it leaks state and ignores cancellation.
- Prefer `withContext(Dispatchers.Default)` for CPU work, `Dispatchers.IO` (JVM/Android) or `Dispatchers.Default` (everywhere else) for blocking IO.

## Resources

- Shared images / strings / fonts go in `composeApp/src/commonMain/composeResources/`
- Per-platform Android resources live under `composeApp/src/androidMain/res/` and are reserved for things that can only be Android resources (launcher icon, themes, manifest references)
- iOS-only assets belong in `iosApp/iosApp/Assets.xcassets/`

## Versions and dependencies

1. Edit only `gradle/libs.versions.toml`
2. Reference dependencies via the typesafe accessor: `implementation(libs.compose.material3)`
3. Reference plugins via `alias(libs.plugins.composeMultiplatform)`
4. Group related libraries under a shared version when they release together (e.g., the `composeMultiplatform` and `androidx-lifecycle` versions)

## Comments

- **Don't comment what the code does** — the code already says that
- **Do comment why** when the reason is non-obvious: a constraint, a workaround, a deliberate API choice
- KDoc (`/** ... */`) for public APIs that other contributors will call
- TODOs: `// TODO(<owner>): <action>` — never bare `// TODO`

## Logging

There's no multiplatform logger wired in. Use `println` while bootstrapping. Once you adopt a logger (Napier, Kermit), update [Technologies](TECHNOLOGIES.md) and replace `println` calls in one PR.

## Realtime subscriptions

When adding a new live feed against a Supabase table:

1. Subscribe to `supabase_realtime` for that table, **always filtered by `meetup_id`** so two concurrent rooms don't cross-contaminate.
2. Wrap the subscription in `try { … } finally { withContext(NonCancellable) { channel.unsubscribe() } }` so cancellations don't leak channels.
3. For the simplest correct implementation, re-fetch via REST on every change instead of diff-applying — it's cheap at room sizes we expect, and keeps state derivation linear.

Reference implementations: `MeetupRepository.observeAll()`, `ParticipantRepository.observe(meetupId)`. Both follow [Architecture → Realtime data flow](ARCHITECTURE.md).

## Tests

See [Testing Guide](TESTING_GUIDE.md). Standards summary:

- Tests in `commonTest` use `kotlin.test` only
- Test class name = production class name + `Test` (`MeetupRepository` → `MeetupRepositoryTest`)
- One behavior per test method
- Arrange / Act / Assert structure with blank lines separating sections

## Build hygiene

- Don't commit `local.properties`, `*.iml`, `xcuserdata/`, `.idea/`, or `.gradle/`
- Don't add a dependency that resolves only on one target unless it's wired into the matching platform source set
- Don't disable lint or warnings to silence a problem — fix the root cause
- Don't use `-PskipKotlinNativeBuild` or similar tricks unless the corresponding doc page explains why
