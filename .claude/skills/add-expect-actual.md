---
name: add-expect-actual
description: Create an expect declaration in commonMain plus actual implementations for every active target
---

# Skill: `/add-expect-actual`

Add a multiplatform contract: `expect` in `commonMain`, `actual` in every platform source set. Active targets in this repo: **androidMain, iosMain, jvmMain, jsMain, wasmJsMain**.

## When to use

The user asks to "expose a platform API to shared code", "add a multiplatform abstraction for X", or any task that requires reading platform-only types (Android `Context`, iOS `UIKit`, JVM `java.*`, browser `window`) from `commonMain`.

## Pre-flight

Before adding a new `expect`, ask: **does a multiplatform library already do this?**

- Time / clock → `kotlinx-datetime`
- HTTP → `Ktor`
- Filesystem → `kotlinx-io` or `okio`
- Settings / preferences → `multiplatform-settings`
- Database → `SQLDelight`, `Room KMP`, `Realm`

If yes, recommend the library and **don't** add the `expect`. If no, proceed.

## Inputs to confirm

- **Name** of the API surface (e.g., `Clock`, `SecureStorage`, `BiometricAuth`)
- **Smallest possible signature** — one or two functions, or a tight interface
- **Targets to support** — confirm all five active or a subset

## Procedure

### 1. Author the `expect` in `commonMain`

```kotlin
// commonMain/kotlin/com/xergioalex/kmpstarter/storage/SecureStorage.kt
package com.xergioalex.kmpstarter.storage

expect class SecureStorage {
    fun getString(key: String): String?
    fun setString(key: String, value: String)
    fun remove(key: String)
}
```

Or top-level functions:

```kotlin
// commonMain/kotlin/com/xergioalex/kmpstarter/clock/Clock.kt
package com.xergioalex.kmpstarter.clock

expect fun nowMillis(): Long
```

Rules:

- `expect` lives **only** in `commonMain`
- Keep the surface minimal — every method multiplies maintenance work by 5
- Prefer top-level functions to classes when there's no instance state

### 2. Add `actual` for **every** active target

The five files. Always create all of them in the same commit.

#### androidMain

```kotlin
// androidMain/kotlin/com/xergioalex/kmpstarter/storage/SecureStorage.android.kt
package com.xergioalex.kmpstarter.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class SecureStorage(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    actual fun getString(key: String): String? = prefs.getString(key, null)
    actual fun setString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    actual fun remove(key: String) { prefs.edit().remove(key).apply() }
}
```

If the `actual` requires a parameter only available on Android (`Context`), the constructor signature differs from `commonMain`. The compiler accepts this — but the **caller in `commonMain` can't construct it**. Solution: provide a factory in a separate `expect`, or expose the instance via DI / `LocalProvider`.

#### iosMain

```kotlin
// iosMain/kotlin/com/xergioalex/kmpstarter/storage/SecureStorage.ios.kt
package com.xergioalex.kmpstarter.storage

import platform.Foundation.NSUserDefaults

actual class SecureStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(key)
    actual fun setString(key: String, value: String) { defaults.setObject(value, forKey = key) }
    actual fun remove(key: String) { defaults.removeObjectForKey(key) }
}
```

> For real secure storage on iOS use the Keychain (`platform.Security.SecItem*`). `NSUserDefaults` shown here for brevity — replace before shipping.

#### jvmMain

```kotlin
// jvmMain/kotlin/com/xergioalex/kmpstarter/storage/SecureStorage.jvm.kt
package com.xergioalex.kmpstarter.storage

import java.util.prefs.Preferences

actual class SecureStorage {
    private val prefs = Preferences.userRoot().node("com/xergioalex/kmpstarter")

    actual fun getString(key: String): String? = prefs.get(key, null)
    actual fun setString(key: String, value: String) { prefs.put(key, value) }
    actual fun remove(key: String) { prefs.remove(key) }
}
```

> For real secure storage on Desktop use the OS keyring; `Preferences` is unencrypted.

#### jsMain

```kotlin
// jsMain/kotlin/com/xergioalex/kmpstarter/storage/SecureStorage.js.kt
package com.xergioalex.kmpstarter.storage

import kotlinx.browser.localStorage

actual class SecureStorage {
    actual fun getString(key: String): String? = localStorage.getItem(key)
    actual fun setString(key: String, value: String) { localStorage.setItem(key, value) }
    actual fun remove(key: String) { localStorage.removeItem(key) }
}
```

> Browser `localStorage` is **not secure** — for tokens use HttpOnly cookies set by the backend. Do not put secrets in localStorage.

#### wasmJsMain

```kotlin
// wasmJsMain/kotlin/com/xergioalex/kmpstarter/storage/SecureStorage.wasmJs.kt
package com.xergioalex.kmpstarter.storage

import kotlinx.browser.localStorage

actual class SecureStorage {
    actual fun getString(key: String): String? = localStorage.getItem(key)
    actual fun setString(key: String, value: String) { localStorage.setItem(key, value) }
    actual fun remove(key: String) { localStorage.removeItem(key) }
}
```

### 3. Filenames

Strict convention:

- `Foo.kt` for the `expect` file in `commonMain`
- `Foo.<platform>.kt` for each `actual` — `Foo.android.kt`, `Foo.ios.kt`, `Foo.jvm.kt`, `Foo.js.kt`, `Foo.wasmJs.kt`

### 4. Add a test

If the abstraction has testable behavior, add a `commonTest` smoke test (it'll run for each test-capable target):

```kotlin
// commonTest/kotlin/com/xergioalex/kmpstarter/clock/ClockTest.kt
package com.xergioalex.kmpstarter.clock

import kotlin.test.Test
import kotlin.test.assertTrue

class ClockTest {
    @Test
    fun nowIsMonotonicallyIncreasing() {
        val a = nowMillis()
        Thread.sleep(1)   // careful — kotlinx-datetime is preferred for actual time logic
        val b = nowMillis()
        assertTrue(b >= a)
    }
}
```

Tests for `actual`s that require platform context (Android `Context`, iOS UIKit) must live in the matching platform's test source set.

### 5. Verify all targets compile

```bash
./gradlew :composeApp:assemble
```

If any target fails with `expected actual is missing` — that's the canonical error for a missing `actual`. Add it.

```bash
./gradlew :composeApp:allTests
```

### 6. Documentation

- If this `expect` is one a contributor will want to know about, mention it in [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md) under "Platform abstraction"
- If the abstraction adds a new dependency (e.g., `androidx.security:security-crypto`), follow `/bump-deps` to add it to `gradle/libs.versions.toml`

## Don't

- Declare `expect` outside `commonMain`
- Skip a platform's `actual` because "we don't ship it yet" — the build fails for that target later
- Inline platform types in `commonMain` (e.g., `actual class Foo(val context: Context)` — `Context` doesn't exist in commonMain). Use a factory or DI to inject the platform-bound instance
- Replace `expect`/`actual` with `if (platform == Android) { ... }` checks at runtime — that's not the Kotlin Multiplatform idiom

## Do

- Keep the `expect` small
- Provide sensible defaults via a factory function in `commonMain` that accepts what the platform needs
- Document the platform-specific tradeoffs in KDoc on the `expect` ("On iOS this uses Keychain; on Web localStorage which is not secure")
- Add a test even if it only verifies non-throwing
