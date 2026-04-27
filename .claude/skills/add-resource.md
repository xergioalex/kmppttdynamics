---
name: add-resource
description: Add a string, drawable, or font to composeResources with translations across all supported locales
---

# Skill: `/add-resource`

Add a Compose Multiplatform resource (string, drawable, font, or arbitrary file) under `composeApp/src/commonMain/composeResources/`. Strings get translated into every supported locale.

## When to use

- The user adds a new user-visible label, button text, error message, etc.
- The user adds an image, icon, or font that should be available on every platform
- The user asks "how do I localize X"

## Resource types

| Resource | Folder | Read with | Generated accessor |
|---|---|---|---|
| String | `values/strings.xml` | `stringResource(Res.string.<name>)` | `Res.string.<name>` |
| Plural | `values/strings.xml` (`<plurals>`) | `pluralStringResource(Res.plurals.<name>, count, count)` | `Res.plurals.<name>` |
| String array | `values/strings.xml` (`<string-array>`) | `stringArrayResource(Res.array.<name>)` | `Res.array.<name>` |
| Drawable | `drawable/` | `painterResource(Res.drawable.<name>)` | `Res.drawable.<name>` |
| Font | `font/` | `Font(Res.font.<name>)` | `Res.font.<name>` |
| Arbitrary file | `files/` | `Res.readBytes("files/<name>")` | path string |

## Procedure for a new string

### 1. Identify supported locales

Check existing folders under `composeApp/src/commonMain/composeResources/`:

```bash
ls composeApp/src/commonMain/composeResources/ | grep '^values'
```

The starter currently has only `values/` (English). If `values-es/`, `values-fr/`, etc. exist, you must add the new key in **every** locale.

### 2. Pick a key

- `snake_case`, valid Kotlin identifier (no hyphens)
- Namespace by feature: `task_list_title`, `settings_dark_mode_label`
- Avoid generic names like `title` — duplicates aren't allowed

### 3. Add to default `values/strings.xml`

```xml
<!-- composeApp/src/commonMain/composeResources/values/strings.xml -->
<resources>
    <!-- existing entries... -->
    <string name="task_list_title">Tasks</string>
</resources>
```

### 4. Add to each additional locale

For each `values-<locale>/strings.xml` that exists, add the translated entry. **Don't skip a locale** — Compose-MP will fail to find the key for users on that locale.

```xml
<!-- composeApp/src/commonMain/composeResources/values-es/strings.xml -->
<string name="task_list_title">Tareas</string>
```

If you can't translate, ask the user; don't paste the English value as a placeholder.

### 5. Use it

```kotlin
import org.jetbrains.compose.resources.stringResource
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.task_list_title

@Composable
fun MyHeader() {
    Text(stringResource(Res.string.task_list_title))
}
```

If you need it from a non-composable context (ViewModel, repository):

```kotlin
import org.jetbrains.compose.resources.getString
suspend fun loadTitle() = getString(Res.string.task_list_title)
```

### 6. Format placeholders

```xml
<string name="welcome_message">Hello, %1$s! You have %2$d tasks.</string>
```

```kotlin
Text(stringResource(Res.string.welcome_message, name, count))
```

Always use **positional** placeholders (`%1$s`, `%2$d`) — translators can reorder them.

### 7. Build

```bash
./gradlew :composeApp:assemble
```

If you see `Unresolved reference: <key>`, the resource generator hasn't picked up the new XML — clean and reassemble:

```bash
./gradlew clean :composeApp:assemble
```

## Procedure for a new plural

```xml
<!-- values/strings.xml -->
<plurals name="task_count">
    <item quantity="one">%1$d task</item>
    <item quantity="other">%1$d tasks</item>
</plurals>

<!-- values-es/strings.xml -->
<plurals name="task_count">
    <item quantity="one">%1$d tarea</item>
    <item quantity="other">%1$d tareas</item>
</plurals>
```

```kotlin
Text(pluralStringResource(Res.plurals.task_count, count, count))
```

`quantity` keys follow [CLDR rules](https://cldr.unicode.org/index/cldr-spec/plural-rules) — every locale needs the categories that locale uses (`zero`, `one`, `two`, `few`, `many`, `other`).

## Procedure for a new drawable

1. Drop the file in `composeApp/src/commonMain/composeResources/drawable/<name>.<ext>`
   - SVG / PNG / JPG / XML vector all work
   - Filename is the accessor: `add_task.svg` → `Res.drawable.add_task`
   - `snake_case` only; no hyphens
2. Use it:

```kotlin
import org.jetbrains.compose.resources.painterResource
Image(
    painter = painterResource(Res.drawable.add_task),
    contentDescription = stringResource(Res.string.add_task),
)
```

3. For density-specific raster images, use qualifier folders: `drawable-mdpi/`, `drawable-xhdpi/`, etc.

## Procedure for a new font

1. Drop the font file in `composeApp/src/commonMain/composeResources/font/<name>.<ext>` (`ttf` or `otf`)
2. Reference:

```kotlin
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.inter_regular

val InterFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_bold, FontWeight.Bold),
)
```

3. Apply in `MaterialTheme`:

```kotlin
MaterialTheme(
    typography = MaterialTheme.typography.copy(
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = InterFamily),
        // ... other styles
    )
) { /* ... */ }
```

## Pitfalls

- **Don't** add `values-<locale>/strings.xml` without filling in every key from `values/strings.xml`. Missing keys fall through to the default, but then your app silently mixes languages
- **Don't** rename a string key after release — analytics and stored prefs may reference it; deprecate with a comment first
- **Don't** put resources outside `composeResources/` — Android-specific resources in `androidMain/res/` are reserved for things only Android resources can do (launcher icon, manifest theme)
- **Don't** use hyphens in filenames or keys — Kotlin can't generate valid identifiers
- **Web target loads resources async.** First read causes a brief flicker; pre-fetch with `getString(...)` at app start if it matters

## Don't

- Hardcode strings in `Text("...")` — always `stringResource(...)`
- Skip translation — ask the user before checking in untranslated keys
- Mix density-specific drawables with vector drawables for the same asset (vector wins; pick one path)

## Do

- Use feature-prefixed keys: `task_list_title`, `settings_dark_mode_label`
- Prefer vector drawables (SVG/XML) over raster — they scale on every platform
- Update [docs/I18N_GUIDE.md](../../docs/I18N_GUIDE.md) if you add a new locale
