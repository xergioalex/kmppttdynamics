# Accessibility

Compose Multiplatform inherits a11y from each host (TalkBack on Android, VoiceOver on iOS, the OS screen reader on Desktop, ARIA on Web). The composables you write are responsible for **giving the host enough information**.

Targets: WCAG 2.1 AA at minimum. Material 3 components ship with a11y baked in — your job is to not break it and to handle custom components correctly.

## Core rules

### 1. Use Material 3 components

`Button`, `IconButton`, `TextField`, `Switch`, `Checkbox`, `Slider` from `androidx.compose.material3.*` already:

- Set the correct semantic role (button, switch, etc.)
- Have a 48dp / 48pt minimum touch target
- Honor `MaterialTheme.colorScheme` contrast tokens

If you find yourself writing `Box(Modifier.clickable { ... })` to mimic a button, **use `Button` or `IconButton` instead**.

### 2. Content descriptions

Every `Image` and `Icon` must have a `contentDescription`. Choose:

- **Informative image:** describe what it conveys — `contentDescription = stringResource(Res.string.user_avatar)`
- **Decorative image:** `contentDescription = null` — screen readers skip it
- **Icon inside a labeled button:** `contentDescription = null` on the icon (the button label covers it)
- **Icon-only button:** the icon's `contentDescription` IS the button's accessible name

```kotlin
IconButton(onClick = onDelete) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = stringResource(Res.string.delete_task),
    )
}
```

### 3. Touch targets

Material 3 components default to 48dp/48pt. For custom click targets:

```kotlin
Modifier
    .minimumInteractiveComponentSize()    // Enforces 48dp minimum hit area
    .clickable { ... }
```

Or expand the hit area:

```kotlin
Modifier.padding(8.dp).size(40.dp).clickable { ... }   // ❌ 40dp target, fails WCAG
Modifier.size(48.dp).clickable { ... }                 // ✅
```

### 4. Color contrast

Use `MaterialTheme.colorScheme.*`:

- `onSurface` / `surface`, `onPrimary` / `primary`, `onError` / `error` — all guaranteed AA
- Don't hardcode `Color(0xFF888888)` for body text — it almost certainly fails WCAG against your background

If you must use a custom color, verify with a contrast checker (e.g., [WebAIM](https://webaim.org/resources/contrastchecker/)). Targets:

- **Normal text (<18pt):** 4.5:1
- **Large text (≥18pt or ≥14pt bold):** 3:1
- **UI components / graphics:** 3:1

### 5. Semantic structure

Use `Modifier.semantics { ... }` for custom components:

```kotlin
Box(
    modifier = Modifier
        .clickable(onClickLabel = stringResource(Res.string.expand_task)) { onToggle() }
        .semantics {
            role = Role.Button
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        }
)
```

Common semantics properties:

- `role` — `Button`, `Checkbox`, `Switch`, `Tab`, `Image`, `RadioButton`, `Image`
- `stateDescription` — current state in human terms ("Selected", "Off")
- `contentDescription` — what the element represents
- `onClickLabel` — what tapping does ("Expand task")
- `liveRegion` — `LiveRegionMode.Polite` for status updates that should be announced

### 6. Headings

Mark headings so screen readers can navigate:

```kotlin
Text(
    text = "Today's Tasks",
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.semantics { heading() }
)
```

### 7. Focus order

For custom layouts, set focus order explicitly:

```kotlin
val (firstField, secondField) = FocusRequester.createRefs()

TextField(
    value = ...,
    onValueChange = ...,
    modifier = Modifier
        .focusRequester(firstField)
        .focusProperties { next = secondField }
)
```

### 8. RTL

Always use logical modifiers:

- `padding(start = 8.dp, end = 8.dp)` — flips correctly in RTL
- `padding(left = 8.dp, right = 8.dp)` — does NOT flip; avoid

`AndroidManifest.xml` already sets `android:supportsRtl="true"`. Keep it on.

## Per-platform notes

### Android (TalkBack)

- Test with TalkBack: Settings → Accessibility → TalkBack
- Use the **Accessibility Scanner** app to spot issues
- Compose maps semantics → `AccessibilityNodeInfo` automatically; the manual mapping is rare

### iOS (VoiceOver)

- Test on a real device: Settings → Accessibility → VoiceOver (or use the Accessibility Inspector in Xcode)
- Compose-MP maps semantics → UIKit accessibility traits automatically
- Some edge cases (nested clickable rows) need explicit `Modifier.semantics(mergeDescendants = true) { }` so VoiceOver treats the row as one element

### Desktop

- Screen reader support is OS-dependent; macOS VoiceOver and Windows Narrator both work via Compose's a11y bridge
- Keyboard navigation matters more on desktop — every interactive element should be reachable via Tab

### Web

- Compose-MP for Wasm/JS maps semantics → DOM ARIA attributes
- Test with a screen reader in the browser (NVDA on Windows, VoiceOver on macOS, ChromeVox on ChromeOS)
- Browser DevTools' Accessibility panel shows the computed accessibility tree
- Lighthouse a11y audit catches common issues — target a score of 95+

## Patterns

### Disclosure (expandable section)

```kotlin
@Composable
fun ExpandableCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { onToggle() }
            .semantics {
                role = Role.Button
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
    ) {
        Text(title)
        AnimatedVisibility(expanded) { content() }
    }
}
```

### Live status updates

```kotlin
Text(
    text = statusMessage,
    modifier = Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
    }
)
```

The screen reader announces the new value when `statusMessage` changes.

### Form errors

```kotlin
TextField(
    value = email,
    onValueChange = onEmailChange,
    isError = !emailValid,
    supportingText = if (!emailValid) {
        { Text(stringResource(Res.string.email_invalid)) }
    } else null
)
```

`isError` sets the correct semantic state; the `supportingText` becomes the announcement.

## Testing

- Manual: walk every screen with TalkBack/VoiceOver enabled before shipping a major feature
- Automated: Compose UI tests can assert semantics — `onNodeWithContentDescription("Delete")` matches by accessibility name

## Don't

- ❌ `Modifier.clickable { }` on a `Box` representing a button
- ❌ `contentDescription = "icon"` (meaningless to a screen reader)
- ❌ `Color(0xFFAAAAAA)` for text on a white background
- ❌ Touch targets smaller than 48dp/48pt
- ❌ Removing focus indicators ("looks cleaner") — they're vital for keyboard users
- ❌ Conveying meaning with color alone (e.g., red text without a label/icon)
- ❌ `Modifier.padding(left = ...)` (use `start`)

## Do

- ✅ Use Material 3 components by default
- ✅ Provide `contentDescription` for every meaningful image/icon
- ✅ Use `MaterialTheme.colorScheme` for colors
- ✅ Mark headings with `Modifier.semantics { heading() }`
- ✅ Test with the actual screen reader on each platform you ship
- ✅ Keep focus visible (Material 3 components do this by default)
