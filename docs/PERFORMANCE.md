# Performance

Performance is a property of the whole app — UI rendering, build size, startup time, network. This guide covers the levers you have in a Compose Multiplatform app and the defaults that already ship with this starter.

## Compose UI: keep recomposition cheap

Compose recomposes any composable whose *unstable* inputs change. The optimization game is mostly about helping the compiler skip unchanged composables.

### Stable types

A type is **stable** if Compose can compare its instances cheaply and trust they're immutable.

- `data class` with `val` properties of stable types ✅
- `Int`, `String`, `Boolean`, primitive numbers ✅
- Functional types (`(Int) -> Unit`) — stable since Compose Compiler 1.4
- `MutableStateFlow`, `StateFlow` ✅
- `MutableState<T>` where `T` is stable ✅

A type is **unstable** if any property is `var`, or it contains a non-stable type (collections by default).

```kotlin
data class MeetupParticipant(
    val id: String,
    val displayName: String,
    val isOnline: Boolean,
)   // ✅ stable

data class ParticipantsView(
    var items: List<MeetupParticipant>,
)   // ❌ unstable — `var` and `List` is unstable by default
```

### Annotations

When the compiler can't infer stability:

```kotlin
@Immutable
data class ParticipantsView(val items: List<MeetupParticipant>)
```

`@Immutable` is a contract — you're promising Compose the type never changes. `@Stable` is weaker — instances are comparable but may change in stability-tracked ways.

For collections, prefer `kotlinx.collections.immutable` (`ImmutableList`, `PersistentList`) which are auto-stable.

### Skip recomposition with `key`

`key()` and `LazyColumn { items(list, key = { it.id }) }` let Compose reuse compositions instead of recreating them.

### Don't read state too high in the tree

Reading `viewModel.uiState.collectAsState()` at the screen root recomposes the whole screen on every change. Push state reads down to the smallest composable that needs them — Compose only recomposes that subtree.

### `derivedStateOf`

```kotlin
val isFormValid by remember(name, email) {
    derivedStateOf { name.isNotEmpty() && email.contains("@") }
}
```

This recomputes `isFormValid` only when `name` or `email` change, and only triggers recomposition of consumers when the *result* changes — useful for expensive derivations.

### `remember`

Avoid recomputing per recomposition:

```kotlin
val formatted = remember(timestamp) { formatRelative(timestamp) }
```

## Lazy lists

- `LazyColumn` / `LazyRow` for any list that can scroll
- Provide `key` lambdas — without them, scroll position resets on data updates
- Use `contentType` when items have varied layouts so Compose can recycle them

```kotlin
LazyColumn {
    items(tasks, key = { it.id }, contentType = { "task" }) { task ->
        TaskRow(task)
    }
}
```

## Compose compiler reports

When in doubt, ask the compiler what it thinks:

```kotlin
// composeApp/build.gradle.kts
kotlin {
    targets.withType<org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget> {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.addAll(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                        layout.buildDirectory.dir("compose_reports").get().asFile.absolutePath
                )
            }
        }
    }
}
```

Then `./gradlew :composeApp:compileKotlinJvm` and inspect `composeApp/build/compose_reports/` for stability and skippability reports.

## State containers

- **Prefer `StateFlow`** for screen state — it has a current value, supports `collectAsStateWithLifecycle()`, and de-duplicates equal emissions
- **Use `viewModelScope`** so coroutines cancel with the screen
- **Avoid `MutableState` from a non-UI thread** — Compose state updates are not thread-safe; use `Snapshot.withMutableSnapshot { }` or marshal to the UI thread

## Android specifics

### R8 / ProGuard for release

This starter ships with `isMinifyEnabled = false` for release builds — fast iteration but bloated production APKs. To enable R8:

```kotlin
buildTypes {
    getByName("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

Add `composeApp/proguard-rules.pro` with rules for any reflection-using libraries (Ktor, kotlinx-serialization, etc.). See [Build & Deploy → Android](BUILD_DEPLOY.md#android).

### Baseline profiles

For Android, a baseline profile speeds up cold start by 20-40%. Consider adding `androidx.benchmark:benchmark-macro-junit4` and generating a profile after stabilizing your main flow.

### Startup

- `enableEdgeToEdge()` runs before `setContent` — keep it cheap
- `App()` should not do IO — defer with `LaunchedEffect`
- Use Compose-Multiplatform's split-rendering (composables stream in) for faster first frame

## iOS specifics

- Static framework startup is faster than dynamic — already set (`isStatic = true`)
- Avoid touching UIKit types from `commonMain`; the bridge has cost
- Prefer Material 3 over hand-rolling layouts that mimic UIKit — Material 3 is heavily optimized

## Web specifics

### Wasm > JS

Wasm bundle is roughly **half the size** and **2-3× faster** for Compose UI than the JS target. Default to Wasm; ship JS only as a fallback for browsers without Wasm GC.

### Bundle size

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
ls -lh composeApp/build/dist/wasmJs/productionExecutable/
```

The Wasm output is usually a few MB. To shrink:

- Don't pull in JVM-heavy libraries unless they have small Wasm targets
- Strip unused Compose components — the `material3` artifact is large but tree-shakes; the same is not true of `material-icons-extended`
- Enable Brotli compression at your CDN

### Caching

Content-hash all assets (the build does this) and serve `index.html` with `Cache-Control: no-cache`. Everything else can be `max-age=31536000, immutable`.

## Avatar bundle

The 132 avatars in `composeApp/src/commonMain/composeResources/files/avatars/` are part of the bundle on every platform. Source files in `assets/avatars/all/` are 318×318 PNGs (21 MB total — too big to ship). Each release of the avatar pack should pass through:

```bash
DEST=composeApp/src/commonMain/composeResources/files/avatars
mkdir -p "$DEST"
TMP=$(mktemp -d)
for f in assets/avatars/all/*.png; do
    sips -Z 192 "$f" --out "$TMP/$(basename "$f")" >/dev/null
done
for f in "$TMP"/*.png; do
    pngquant --quality=70-90 --speed 1 \
        --output "$DEST/$(basename "$f")" --force "$f" \
        || cp "$f" "$DEST/$(basename "$f")"
done
```

(macOS-only; on other systems substitute ImageMagick / Sharp.) The pipeline above takes 21 MB → 3.5 MB at 192×192 with negligible visible quality loss. Always re-run it before committing new avatars and confirm the bundle stays small.

`AvatarImage` decodes each PNG once via `Res.readBytes(...) + decodeToImageBitmap()`, then `remember`s the resulting `ImageBitmap` — re-composition inside the picker grid (132 cells) doesn't re-decode. Keep that pattern when adding any other bundled-image lookup.

## Desktop specifics

### Hot reload caveats

Hot reload skips composables that captured state across recomposition. If a hot-reload edit "doesn't take," restart the app — it's a development feature, not production-grade.

### Memory

- Default heap is JVM-default. For data-heavy desktop apps add `-Xmx2g` to the application's JVM args
- Skia caches glyph data — initial render of a new font is slower

## Measuring

| What | Tool |
|---|---|
| Android frame timing | Android Studio Profiler → CPU & Energy, or `adb shell dumpsys gfxinfo` |
| iOS frame timing | Xcode Instruments → SwiftUI / Animation Hitches |
| Compose recomposition counts | `Modifier.composed { /* increment counter */ }` or Compose Compiler reports |
| Web bundle size | `du -sh composeApp/build/dist/wasmJs/productionExecutable/` |
| Web runtime perf | Chrome DevTools → Performance |
| Build performance | `./gradlew :composeApp:assemble --scan` |

## Pitfalls

1. **Reading state at the root of `App()` means full-tree recomposition.** Push reads down.
2. **Lambdas allocated inside composables** are stable since Compose Compiler 1.4 — but only if the composable is itself skippable. Check the compiler report.
3. **`Column` over a long list** — always migrate to `LazyColumn`.
4. **`Modifier.background(...).clip(...)` clips the background.** Order modifiers carefully.
5. **`isMinifyEnabled = false` in production Android builds.** Always enable R8 before shipping.
6. **Hardcoded string concatenation.** `Text("Hello, $name")` recomposes on every name change; that's fine. But `Text(stringResource(...).format(name))` allocates a new String each time — usually negligible, but not in tight loops.
7. **`remember` with no key.** `remember { computeOnce() }` actually re-runs across navigation events when the composition is destroyed and recreated; use `rememberSaveable` for state that should survive.
