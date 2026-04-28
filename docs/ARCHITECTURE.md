# Architecture

This document explains the **big picture** of KMPPTTDynamics so a new contributor (human or agent) can be productive quickly. For day-to-day commands see [Development Commands](DEVELOPMENT_COMMANDS.md). For language-specific rules see [Standards](STANDARDS.md).

## High-level model

```
                          ┌────────────────────────────┐
                          │  composeApp/commonMain     │
                          │  ────────────────────────  │
                          │  • App.kt   (root @Comp.)  │
                          │  • AppContainer.kt (DI)    │
                          │  • Platform.kt (expect)    │
                          │  • domain/, supabase/,     │
                          │    meetups/, participants/ │
                          │  • ui/{home,create,join,   │
                          │    room,theme,components}  │
                          │  • composeResources/       │
                          └────────────┬───────────────┘
                                       │ same Kotlin code
   ┌──────────────┬──────────────┬────┴────┬──────────────┬──────────────┐
   ▼              ▼              ▼         ▼              ▼              ▼
androidMain    iosMain        jvmMain   webMain        jsMain        wasmJsMain
  ↓              ↓              ↓         ↓              ↓              ↓
MainActivity   MainView-     application  Compose-     (only        (only
.setContent    Controller    { Window }   Viewport      actual       actual
  { App(c) }   { App(c) }    { App(c) }   { App(c) }    }              }
   ↓              ↓              ↓         ↓
Android APK    ComposeApp    Desktop      Web JS bundle
                .framework   installer    +
                (consumed    (Dmg/Msi/    Web Wasm bundle
                 by Xcode)    Deb)
```

Every platform mounts the **same** `App()` composable. Platform source sets only contain the entry-point glue and `actual` declarations.

## Project structure

```
KMPPTTDynamics/
├── AGENTS.md                          # Single source of truth for AI agents
├── CLAUDE.md → AGENTS.md              # Symlink (do not edit directly)
├── README.md                          # Human-facing intro
├── build.gradle.kts                   # Root build script (declares plugins, applies none)
├── settings.gradle.kts                # Module list + repositories + TYPESAFE_PROJECT_ACCESSORS
├── gradle.properties                  # JVM args, configuration cache, AndroidX flags
├── gradle/
│   ├── libs.versions.toml             # Single version catalog — pin all deps here
│   └── wrapper/                       # Gradle wrapper jar + properties
├── gradlew, gradlew.bat               # Wrapper launchers
├── local.properties                   # Android SDK path (gitignored)
│
├── composeApp/                        # The only Gradle subproject
│   ├── build.gradle.kts               # KMP target list, source-set deps, Android config
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/xergioalex/kmppttdynamics/
│       │   │   ├── App.kt                 # @Composable App(container) — entry composable + onboarding gate
│       │   │   ├── AppContainer.kt        # DI-lite holder (settings + lazy repos + presence tracker)
│       │   │   ├── JoinCodeGenerator.kt   # 6-char codes excluding 0/O/1/I
│       │   │   ├── Platform.kt            # expect fun getPlatform()
│       │   │   ├── domain/                # Meetup, AppUser, MeetupParticipant, … (kotlinx-serialization)
│       │   │   ├── supabase/              # SupabaseClientProvider, RealtimeChannelNames (uniqueRealtimeTopic)
│       │   │   ├── appusers/              # AppUserRepository — cross-meetup profile + realtime
│       │   │   ├── meetups/               # MeetupRepository — REST + realtime channel
│       │   │   ├── participants/          # ParticipantRepository — find-then-update join, claim, role
│       │   │   ├── chat/, handraise/, qa/, polls/, raffles/  # one repo per realtime feed
│       │   │   ├── presence/              # GlobalPresenceTracker (Realtime Presence on `app_lobby`)
│       │   │   ├── settings/AppSettings   # theme + profile + per-meetup cache + installClientId
│       │   │   └── ui/                    # onboarding, home, create, room, theme, components
│       │   └── composeResources/
│       │       ├── drawable/              # ptt_logo_vertical.png, ptt_logo_horizontal.png
│       │       ├── files/avatars/         # 132 bundled PNGs (192×192, ~3.5 MB total)
│       │       ├── values/strings.xml     # English
│       │       └── values-es/strings.xml  # Spanish
│       ├── commonTest/kotlin/             # Shared kotlin.test tests (e.g. JoinCodeGeneratorTest, SerializationTest)
│       │
│       ├── androidMain/
│       │   ├── kotlin/com/xergioalex/kmppttdynamics/
│       │   │   ├── PttApplication.kt  # Owns AppContainer (survives MainActivity recreation)
│       │   │   ├── MainActivity.kt    # ComponentActivity → setContent { App((application as PttApplication).container) }
│       │   │   └── Platform.android.kt   # actual fun getPlatform()
│       │   ├── AndroidManifest.xml    # PttApplication + single MainActivity
│       │   └── res/                   # Android-only resources (icons, strings)
│       │
│       ├── iosMain/kotlin/com/xergioalex/kmppttdynamics/
│       │   ├── MainViewController.kt  # fun MainViewController() = ComposeUIViewController { App() }
│       │   └── Platform.ios.kt        # actual fun getPlatform()
│       │
│       ├── jvmMain/kotlin/com/xergioalex/kmppttdynamics/
│       │   ├── main.kt                # application { Window { App() } }
│       │   └── Platform.jvm.kt        # actual fun getPlatform()
│       │
│       ├── webMain/                   # SHARED web entry (used by both JS and Wasm)
│       │   ├── kotlin/com/xergioalex/kmppttdynamics/main.kt   # ComposeViewport { App() }
│       │   └── resources/index.html   # Web shell (loads composeApp.js)
│       │
│       ├── jsMain/kotlin/com/xergioalex/kmppttdynamics/
│       │   └── Platform.js.kt         # actual fun getPlatform()
│       │
│       └── wasmJsMain/kotlin/com/xergioalex/kmppttdynamics/
│           └── Platform.wasmJs.kt     # actual fun getPlatform()
│
├── iosApp/                            # Xcode project — consumes the ComposeApp framework
│   ├── iosApp.xcodeproj/
│   ├── iosApp/
│   │   ├── iOSApp.swift               # @main SwiftUI App
│   │   ├── ContentView.swift          # ComposeView wraps MainViewControllerKt.MainViewController()
│   │   ├── Info.plist
│   │   └── Assets.xcassets/
│   └── Configuration/                 # Build configurations
│
├── docs/                              # This documentation
└── tmp/                               # Git-ignored scratch space
```

## Source sets and the Kotlin Multiplatform hierarchy

Compose Multiplatform uses the default KMP source-set hierarchy. From most-shared to most-specific:

| Source set | Compiled for | Sees | Used for |
|---|---|---|---|
| `commonMain` | every active target | only multiplatform deps | All shared logic, UI, and `expect` declarations |
| `commonTest` | every test-capable target | `commonMain` + test deps | Shared tests |
| `webMain` | `jsMain` + `wasmJsMain` | `commonMain` + browser DOM | Web-only entry point shared by JS and Wasm |
| `androidMain` | Android target | Android SDK + `commonMain` | Android `actual`s, `MainActivity`, manifest |
| `iosMain` | `iosArm64` + `iosSimulatorArm64` | `commonMain` + Apple Foundation/UIKit | iOS `actual`s, `MainViewController` factory |
| `jvmMain` | Desktop JVM target | Java SE + `commonMain` | Desktop entry point and `actual`s |
| `jsMain` | JS target only | `commonMain` + Kotlin/JS DOM | JS-only `actual`s |
| `wasmJsMain` | Wasm target only | `commonMain` + Kotlin/Wasm DOM | Wasm-only `actual`s |

`webMain` is the **shared web** source set: both `jsMain` and `wasmJsMain` automatically depend on it. That's why the entry point (`ComposeViewport { App() }`) lives once in `webMain` and not twice.

## Platform abstraction

The canonical pattern is the contract in `commonMain` plus an `actual` per target:

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

```kotlin
// iosMain/Platform.ios.kt
import platform.UIKit.UIDevice
class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}
actual fun getPlatform(): Platform = IOSPlatform()
```

**When to add a new `expect`:**

- A piece of code in `commonMain` needs a value or behavior that only the platform can provide (clock, file system, secure storage, push token, network engine, etc.)
- The cross-platform abstraction is small (one or two functions / a tight interface)

**When NOT to:**

- The behavior is identical on every platform — just write it in `commonMain`
- The abstraction would balloon — prefer composing from a multiplatform library (Ktor, Kotlinx coroutines, kotlinx-datetime) that already provides cross-platform actuals
- The need is one-off in a single platform's entry point — keep it in that platform source set

## Compose Multiplatform integration

- `commonMain` declares `compose-runtime`, `compose-foundation`, `compose-material3`, `compose-ui`, `compose-components-resources`, and the AndroidX lifecycle Compose libraries — so the same composables compile for every target
- `compose.desktop.currentOs` is added in `jvmMain` for the desktop window APIs
- The `compose-uiTooling` debug dependency is Android-only (Layout Inspector / preview)
- The Compose Compiler plugin is applied via `libs.plugins.composeCompiler` (matched to the Kotlin version)

### Resources

Compose Multiplatform compiles `composeApp/src/commonMain/composeResources/` into a generated `kmppttdynamics.composeapp.generated.resources.Res` object. Subfolder conventions:

- `drawable/` — vector and raster images, accessed via `Res.drawable.<name>`
- `values/strings.xml` — base locale strings, accessed via `Res.string.<name>` (qualifiers like `values-es/` add localized variants)
- `font/` — bundled fonts
- `files/` — arbitrary files (read with `Res.readBytes("files/foo.json")`)

Generated identifiers strip the file extension. For details see [I18N Guide](I18N_GUIDE.md).

### iOS framework bridge

The Kotlin/Native iOS targets emit a static framework named `ComposeApp`. The Xcode project links against it and the Swift side does:

```swift
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
}
```

`MainViewControllerKt` is the auto-generated Objective-C class corresponding to `MainViewController.kt` (Kotlin top-level functions in a file `Foo.kt` are exposed under `FooKt`). If you rename the file or the function, update the Swift call site.

### Web entry split

`webMain` holds the actual `main()` and `index.html`. `jsMain` and `wasmJsMain` only contain a one-liner `Platform.<target>.kt`. This avoids duplicating the bootstrap when adding a third web variant in the future.

## Build configuration highlights

`composeApp/build.gradle.kts`:

- Targets enabled: `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`, `jvm()`, `js { browser() }`, `wasmJs { browser() }` — all with executables
- iOS framework name `ComposeApp`, `isStatic = true`
- Android: `compileSdk 36`, `minSdk 24`, `targetSdk 36`, Java 11; release build `isMinifyEnabled = false` (flip on for production — see [Performance](PERFORMANCE.md))
- Desktop: target formats `Dmg`, `Msi`, `Deb`; main class `com.xergioalex.kmppttdynamics.MainKt`
- BuildKonfig (`com.codingfeline.buildkonfig`): generates a `BuildConfig` Kotlin object at `com.xergioalex.kmppttdynamics.config.BuildConfig` with `SUPABASE_URL` + `SUPABASE_PUBLISHABLE_KEY` read from `.env` at the repo root (or env vars in CI). `exposeObjectWithName` is intentionally NOT set — the resulting `@JsExport` annotation breaks Kotlin/Wasm compilation on standalone objects.
- ktor 3.2.3 engines per platform: CIO (Android + JVM), Darwin (iOS), JS (JS + Wasm). supabase-kt depends on these for transport. Ktor must stay on the minor that matches supabase-kt's BOM — older minors (e.g. 3.0.3) crash the iOS framework at runtime with `IrLinkageError: dropCompressionHeaders`.

`gradle.properties`:

- `kotlin.code.style=official`
- `org.gradle.configuration-cache=true` and `org.gradle.caching=true` — fast builds, but be careful not to capture environment-dependent values in build logic
- `android.nonTransitiveRClass=true`, `android.useAndroidX=true`

`settings.gradle.kts`:

- `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` — reference modules as `projects.composeApp` instead of `project(":composeApp")`
- Foojay toolchain resolver for automatic JDK provisioning

## Realtime data flow

Every screen that watches live data follows the same shape:

```kotlin
fun observe(meetupId: String): Flow<List<X>> = flow {
    val channel = supabase.channel(uniqueRealtimeTopic("x_$meetupId"))
    val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "x"
        filter("meetup_id", FilterOperator.EQ, meetupId)
    }
    channel.subscribe()
    try {
        emit(fetch(meetupId))                       // initial REST snapshot
        kotlinx.coroutines.delay(750)               // catch-up window
        emit(fetch(meetupId))                       // catch-up against JOIN-handshake race
        changes.collect { emit(fetch(meetupId)) }   // refresh on every change
    } finally {
        withContext(NonCancellable) { channel.unsubscribe() }
    }
}
```

Reference implementations: `MeetupRepository.observeAll()`, `ParticipantRepository.observe(meetupId)`, `AppUserRepository.observeAll()`. Eight repositories follow this template.

Three pieces of the template are non-negotiable:

1. **`uniqueRealtimeTopic(base)` for every channel name.** Calling `supabase.channel("static_name")` makes supabase-kt return a shared `RealtimeChannel`; the first consumer to cancel runs `unsubscribe()` and silences every other listener. This was the root cause of the avatar picker freezing on second open.
2. **The 750 ms catch-up emit.** `subscribe()` is fire-and-forget; the websocket JOIN handshake completes asynchronously. Any UPDATE between the initial REST fetch and the handshake completion is delivered to a not-yet-listening channel and dropped. Re-fetching after a short delay closes the gap.
3. **`try/finally` with `NonCancellable`.** Without it, a cancelled flow leaks the Realtime subscription. ViewModels collect these flows via `viewModelScope.launch` so cancellation flows naturally on screen exit; the cleanup itself must run inside a non-cancellable scope or it never reaches the wire.

For the full background, gotchas, and an audit checklist when adding new feeds, read [Realtime patterns](REALTIME_PATTERNS.md). The Presence-based lobby counter (`GlobalPresenceTracker`) follows a different pattern — see that page for the differences.

## Identity model

The user's identity has three layers (full detail in [Identity & avatars](IDENTITY_AND_AVATARS.md)):

| Layer | Storage | Example |
|---|---|---|
| Install client id | `AppSettings.installClientId()` (multiplatform-settings) | `5b40d1a37c83f2ee` |
| App user | `public.app_users` row keyed by `client_id` | `(client_id, "Sergio", avatar_id = 42)` |
| Meetup participant | `public.meetup_participants` row keyed by `(meetup_id, client_id)` (partial unique index) | `(meetup_id, client_id, role = 'host', is_online = true)` |

The install client id is the spine. It's used as:

- The presence key on the `app_lobby` Realtime Presence channel — so reconnects replace, not duplicate, the device.
- The primary key of `app_users` — so changing your avatar is a single upsert.
- The `client_id` on every `meetup_participants` row this device creates — so the partial unique index `(meetup_id, client_id)` makes "joining a meetup" idempotent at the DB layer (no duplicates after re-installs, hot reloads, or `MainActivity` recreation).

`App.kt` reads `container.settings.profile: StateFlow<LocalProfile?>` and gates the entire post-onboarding UI on it. Until the user has picked a name + avatar (and the `app_users` row exists with the unique avatar id), no other screen renders. This is also why there is no longer a `JoinMeetupScreen` — the profile follows the user into every room and `HomeViewModel.onEnterMeetup` auto-joins.

## Mental model summary

1. **Shared by default.** New code goes in `commonMain`; you only descend into a platform source set when forced.
2. **One UI tree.** The same `App(container)` composable runs on all five targets — there is no platform-specific UI graph.
3. **Single version catalog.** All dependency versions live in `gradle/libs.versions.toml`; never inline a literal version string.
4. **`expect`/`actual` is the only platform escape hatch.** If the abstraction is large or one-off, find a multiplatform library or keep it inside the platform's entry point.
5. **The iOS framework name is load-bearing.** `ComposeApp` is referenced from Swift code in `iosApp/`; renaming requires updating both sides.
6. **Supabase is the source of truth.** No offline cache (yet). Every realtime feed in `commonMain` is one channel + REST refresh per change, scoped by `meetup_id`.
7. **`.env` drives the client config.** BuildKonfig reads `SUPABASE_URL` + `SUPABASE_PUBLISHABLE_KEY` at build time — those are the only Supabase values that may enter the app bundle.
