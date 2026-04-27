---
name: add-platform-feature
description: Add a feature that needs a real platform API (network, storage, sensor, push, biometrics)
---

# Skill: `/add-platform-feature`

The user wants a feature that requires platform APIs not available in `commonMain` (e.g., HTTP, secure storage, biometrics, push notifications, file picker, camera). Decide whether to use a multiplatform library or write `expect`/`actual`, then ship the smallest viable implementation.

## When to use

- "Add networking", "save user preferences", "add login with biometric", "save a photo to disk"
- Any task that *would* require importing platform-only types from `commonMain`

## Decision tree

```
Is there a multiplatform library for this?
├── Yes → Use it. Add to libs.versions.toml. Skip to "Wire it up".
└── No  → Write expect/actual following /add-expect-actual.

Multiplatform libraries to consider first:

  HTTP client                  → Ktor                        (io.ktor:ktor-client-*)
  JSON                         → kotlinx-serialization        (org.jetbrains.kotlinx:kotlinx-serialization-json)
  Date/time                    → kotlinx-datetime             (org.jetbrains.kotlinx:kotlinx-datetime)
  IO / Files                   → kotlinx-io or okio
  Settings (key-value)         → multiplatform-settings       (com.russhwolf:multiplatform-settings)
  SQL database                 → SQLDelight                   (app.cash.sqldelight)
  Coroutines                   → kotlinx-coroutines (already pulled by Compose)
  Logging                      → Napier or Kermit
  DI                           → Koin or Kotlin-Inject
  Image loading                → Coil 3 (multiplatform)
  Navigation                   → Voyager or compose-navigation (Decompose for advanced)
  Permissions (Android/iOS)    → moko-permissions
```

## Procedure

### 1. Identify the platform APIs needed

| Feature | Android | iOS | JVM | Web |
|---|---|---|---|---|
| HTTP | OkHttp | NSURLSession | OkHttp / java.net | fetch |
| Secure storage | EncryptedSharedPreferences / Keystore | Keychain | OS keyring / Preferences | HttpOnly cookie (server-set) |
| Biometric | BiometricPrompt | LocalAuthentication.LAContext | OS-specific (rarely shipped) | WebAuthn |
| Push | FCM | APNs | n/a | Web Push |
| Camera | CameraX / Intent | AVFoundation / UIImagePicker | AWT (rare) | getUserMedia |
| File picker | `OpenDocument` Activity | UIDocumentPicker | JFileChooser | `<input type="file">` |

Choose the simplest option that covers your targets.

### 2. Try a library first

```toml
# gradle/libs.versions.toml
[versions]
ktor = "3.4.0"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }    # Android + JVM
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }    # iOS
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }            # JS
# Wasm engine: at the time of writing Ktor for Wasm uses ktor-client-js or a dedicated engine — verify
```

Wire per source set:

```kotlin
// composeApp/build.gradle.kts
sourceSets {
    commonMain.dependencies {
        implementation(libs.ktor.client.core)
    }
    androidMain.dependencies {
        implementation(libs.ktor.client.okhttp)
    }
    iosMain.dependencies {
        implementation(libs.ktor.client.darwin)
    }
    jvmMain.dependencies {
        implementation(libs.ktor.client.okhttp)
    }
    jsMain.dependencies {
        implementation(libs.ktor.client.js)
    }
}
```

### 3. If no library exists — `expect`/`actual`

Follow the [`/add-expect-actual`](add-expect-actual.md) skill. Keep the surface tiny.

### 4. Wire into your feature

Compose-MP idiom: pass dependencies via constructor injection (or a simple service locator):

```kotlin
// commonMain
class TaskRepository(private val client: HttpClient) {
    suspend fun list(): List<Task> = client.get("$BASE/tasks").body()
}
```

The `HttpClient` is constructed at the platform entry point (or in a small DI container) and passed down.

### 5. Permissions and capabilities

#### Android

`composeApp/src/androidMain/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<!-- Add only what you need; document why in the manifest with a comment -->
```

For runtime permissions (camera, location, etc.), use `androidx.activity:activity-compose`'s `rememberLauncherForActivityResult` or `moko-permissions` for multiplatform.

#### iOS

`iosApp/iosApp/Info.plist` — add usage description keys:

- `NSCameraUsageDescription` — string explaining why
- `NSLocationWhenInUseUsageDescription`
- `NSFaceIDUsageDescription`

The user sees this string when iOS prompts for permission. Without it, iOS rejects the call.

#### Desktop / Web

Usually no manifest-level permission, but:

- Web: features like camera, geolocation, notifications require user prompt; only work over HTTPS (localhost OK in dev)
- Desktop: filesystem access is unrestricted; respect platform conventions for storage location

### 6. Test

- Unit-test the shared logic with a fake (e.g., a `FakeHttpClient`)
- Smoke-test on each platform that ships the feature

```bash
./gradlew :composeApp:installDebug                # Android
./gradlew :composeApp:run                         # Desktop
./gradlew :composeApp:wasmJsBrowserDevelopmentRun # Web
# iOS: Xcode ⌘R
```

### 7. Document

- New library or `expect`/`actual` → mention in [`docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) under the relevant section
- New library → row in [`docs/TECHNOLOGIES.md`](../../docs/TECHNOLOGIES.md)
- New permissions → note in [`docs/SECURITY.md`](../../docs/SECURITY.md) (data accessed) and [`docs/PLATFORMS.md`](../../docs/PLATFORMS.md) (per-platform setup)

## Common features — quickstart

### Networking

```kotlin
val client = HttpClient {
    install(ContentNegotiation) { json() }
    install(Logging) { level = LogLevel.INFO }
}
```

### Persistence (key-value)

```kotlin
implementation("com.russhwolf:multiplatform-settings:1.3.0")
implementation("com.russhwolf:multiplatform-settings-no-arg:1.3.0")  // Auto-discovers per platform

val settings: Settings = Settings()
settings.putString("user_id", "abc")
val userId = settings.getStringOrNull("user_id")
```

### SQL

SQLDelight requires a schema definition file (`*.sq`) in `composeApp/src/commonMain/sqldelight/<package>/`. Generate the database driver per platform via `expect`/`actual`. See [SQLDelight docs](https://sqldelight.github.io/sqldelight/2.0.2/multiplatform_sqlite/).

## Don't

- Hand-roll HTTP / JSON / SQL — use Ktor / kotlinx-serialization / SQLDelight
- Forget to add the platform engine (e.g., `ktor-client-darwin` for iOS)
- Skip the `Info.plist` usage description string on iOS (the API call will silently fail)
- Add a permission "in case we need it" — Play Store flags unused permissions

## Do

- Use multiplatform libraries when they exist
- Add platform engines per source set, not in `commonMain`
- Test on each platform you ship
- Document the addition in the right `docs/` file
