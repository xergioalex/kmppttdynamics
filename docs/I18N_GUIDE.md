# Internationalization (i18n)

How to localize KMPTodoApp. The app uses **Compose Multiplatform resources** — the same API works on every target, no per-platform string handling needed.

## Where strings live

```
composeApp/src/commonMain/composeResources/
├── values/
│   └── strings.xml           # Default (English) — required
├── values-es/
│   └── strings.xml           # Spanish translation — optional
└── values-<locale>/
    └── strings.xml           # Any number of locales
```

Locale qualifiers follow the standard [BCP-47](https://en.wikipedia.org/wiki/IETF_language_tag) tags: `values-es`, `values-fr`, `values-pt-rBR` (Portuguese, Brazil).

## Authoring a string

`composeApp/src/commonMain/composeResources/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">KMPTodoApp</string>
    <string name="welcome_message">Hello, %1$s!</string>
    <string name="task_count">You have %1$d tasks</string>
</resources>
```

`values-es/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">KMPTodoApp</string>
    <string name="welcome_message">¡Hola, %1$s!</string>
    <string name="task_count">Tienes %1$d tareas</string>
</resources>
```

## Reading a string in a composable

```kotlin
import org.jetbrains.compose.resources.stringResource
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.welcome_message

@Composable
fun WelcomeBanner(name: String) {
    Text(stringResource(Res.string.welcome_message, name))
}
```

The generated `Res.string.<name>` accessors are produced at build time from `strings.xml`. Underscores stay; the file extension is stripped.

## Reading outside a composable (suspending)

When you need a string in a `ViewModel` or repository:

```kotlin
import org.jetbrains.compose.resources.getString
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.welcome_message

class MyViewModel : ViewModel() {
    suspend fun greet(name: String): String =
        getString(Res.string.welcome_message, name)
}
```

`getString` is `suspend` because resources may need to be loaded asynchronously on web targets.

## Plurals

Compose Multiplatform supports plural strings as of the 1.6+ resource pipeline. Define in `values/strings.xml`:

```xml
<plurals name="task_count">
    <item quantity="one">You have %1$d task</item>
    <item quantity="other">You have %1$d tasks</item>
</plurals>
```

Read:

```kotlin
import org.jetbrains.compose.resources.pluralStringResource
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.task_count

Text(pluralStringResource(Res.plurals.task_count, count, count))
```

The `quantity` keys follow [CLDR rules](https://cldr.unicode.org/index/cldr-spec/plural-rules) (`zero`, `one`, `two`, `few`, `many`, `other`).

## Other resource types

| Type | Folder | Accessor | Read with |
|---|---|---|---|
| String | `values/` | `Res.string.<name>` | `stringResource()` |
| Plural | `values/` | `Res.plurals.<name>` | `pluralStringResource()` |
| String array | `values/` | `Res.array.<name>` | `stringArrayResource()` |
| Drawable | `drawable/` | `Res.drawable.<name>` | `painterResource()` |
| Font | `font/` | `Res.font.<name>` | `Font(Res.font.<name>)` |
| Arbitrary file | `files/` | path string | `Res.readBytes("files/<name>")` |

## Locale qualifiers

You can apply qualifiers beyond language:

- Language only: `values-es`
- Language + region: `values-es-rMX` (note the `r` prefix)
- Density: `drawable-xxhdpi`
- Night mode: `values-night` (dark mode only)

Stack qualifiers as needed: `values-es-rMX-night`.

## Adding a new language

1. **Create the folder.** `composeApp/src/commonMain/composeResources/values-<locale>/`
2. **Copy** `values/strings.xml` into it
3. **Translate** every string. Don't add new keys here that don't exist in the default — the generator complains
4. **Test.** Change device locale (or use `Locale.setDefault(...)` in JVM tests) and verify the right strings load
5. **Update [AGENTS.md](../AGENTS.md)** if the new language affects the multilingual sync rules
6. **Run the build:**

```bash
./gradlew :composeApp:assemble
```

## Locale selection

Compose Multiplatform picks the best-matching locale based on the **system locale**:

- Android: `android.os.LocaleList` (the user's system languages)
- iOS: `NSLocale.preferredLanguages`
- Desktop: `Locale.getDefault()`
- Web: `window.navigator.language`

To override at runtime (in-app language switcher), wrap your UI in a `CompositionLocalProvider` for `LocalConfiguration` (Android) — for true multiplatform overriding, you need an in-app preference and a custom resource resolver. Compose-MP doesn't yet ship a built-in API for this; the typical solution is to set the system locale on app start.

## Translating non-string assets

- **Drawables that contain text** — provide a `drawable-<locale>/foo.png` variant
- **Layouts with locale-specific spacing** — use `LayoutDirection` rather than per-locale layouts; keep RTL in mind (`Modifier.padding(start = ...)`, not `padding(left = ...)`)

## Right-to-left (RTL)

- Android: `android:supportsRtl="true"` (already set)
- Compose: layouts respect `LocalLayoutDirection` automatically when you use `start`/`end` modifiers
- Test by adding an Arabic locale (`values-ar/`) and switching the device language

## Pitfalls

1. **Default `values/` is required.** If you only ship `values-es/` the build fails — `values/` is the fallback for any unsupported locale
2. **Keys must be valid Kotlin identifiers.** `welcome_message` ✅, `welcome-message` ❌
3. **Format placeholders are positional.** `%1$s` is the first arg, `%2$d` the second. Don't reorder placeholders without updating call sites
4. **Web target loading is async.** The first `stringResource(...)` on Web triggers a fetch; brief flicker is normal. To pre-load, call `getString(...)` once at app start
5. **Don't read system locale from `commonMain`.** Use `expect`/`actual` if you need the locale tag itself for analytics

## Linking to AGENTS.md

The agent rule "wrap user-visible strings in `stringResource(...)`" lives in [AGENTS.md](../AGENTS.md#7-multiplatform-resources-mandatory). When you add a new language, also update the multilingual coverage list there if your project has explicit "supported languages" rules.
