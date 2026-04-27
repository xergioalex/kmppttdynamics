# Security

Baseline security guidance for a Kotlin Multiplatform / Compose Multiplatform app. The starter is unconfigured for production — this document is the checklist of what you must add **before** shipping a real product.

## Principles

1. **No secrets in the repo.** Anything that could authenticate as you (API keys, signing keys, certificates) belongs outside the repo
2. **Defense in depth.** Don't rely on a single layer — a leaked API key + missing rate limiting = compromise
3. **Default deny.** Open the smallest network/storage surface needed; expand only when justified
4. **Minimize the trusted dependency surface.** Every dependency is a potential supply-chain risk

## Secrets handling

### Never commit

- API keys, tokens, OAuth client secrets
- Signing keystores (`*.keystore`, `*.jks`, `*.p12`)
- Provisioning profiles (`*.mobileprovision`)
- `local.properties` (already in `.gitignore`)
- `.env` files
- CI deploy tokens

### How to inject secrets at build time

**Option A — Gradle properties (developer-local):**

```properties
# ~/.gradle/gradle.properties (NOT in repo)
KMPTODOAPP_API_KEY=...
```

```kotlin
// composeApp/build.gradle.kts
android {
    defaultConfig {
        buildConfigField(
            "String",
            "API_KEY",
            "\"${providers.gradleProperty("KMPTODOAPP_API_KEY").getOrElse("")}\""
        )
    }
}
```

**Option B — Environment variables (CI):**

```kotlin
val apiKey = System.getenv("KMPTODOAPP_API_KEY") ?: ""
```

**Option C — Compose Multiplatform resources (for non-secret config only):** put values in `composeResources/files/config.json` and read at runtime. Don't put real secrets there — they ship in the app bundle and can be extracted.

### Important: client-side secrets are not secrets

Anything embedded in a mobile/web app can be reverse-engineered out:

- Decompile an Android APK
- Inspect an iOS framework
- Read the Wasm/JS bundle

**Mitigations:**

- Restrict API keys server-side (e.g., Maps SDK keys can be locked to your bundle ID)
- Don't put high-privilege credentials on the client. Use a backend-for-frontend (BFF) and short-lived tokens (OAuth/OIDC)
- Treat client API keys as *identifiers*, not secrets

## Network

### TLS

- All HTTP traffic must be HTTPS in production. Period
- Android: starting at API 28, cleartext is blocked by default. Don't add a `network-security-config.xml` exception unless you have a hard development reason, and never ship one
- iOS: App Transport Security (ATS) is on by default. Don't disable it in `Info.plist`
- Web: HSTS is set by your host/CDN; verify

### Certificate pinning

For high-value endpoints, pin the certificate or public key. Ktor supports pinning via the `OkHttp` engine on Android/JVM and the `Darwin` engine on iOS:

```kotlin
HttpClient(OkHttp) {
    engine {
        config {
            certificatePinner(
                CertificatePinner.Builder()
                    .add("api.example.com", "sha256/...")
                    .build()
            )
        }
    }
}
```

Pin **at least two** certificates (current + backup) to survive rotation.

### CORS (Web)

If your Wasm/JS bundle calls an API on a different origin, the API must send `Access-Control-Allow-Origin`. Lock the allowlist to your production domain — `*` defeats CORS.

## Secure storage

The starter doesn't ship a multiplatform storage layer. Pick the right tool per data class:

| Data | Where |
|---|---|
| Auth tokens | Platform secure storage (Keychain on iOS, EncryptedSharedPreferences on Android, OS keyring on Desktop, sessionStorage / cookies on Web) |
| User preferences | Multiplatform-safe key-value store (`multiplatform-settings`, `DataStore` via KMP-friendly wrapper) |
| Cached data | Local DB (SQLDelight, Room KMP, Realm) |
| Logs | Filesystem with rotation; never persist PII |

### Per-platform secure storage

- **iOS:** `platform.Security.SecKeychain*` from `iosMain`. Wrap behind an `expect class SecureStorage`
- **Android:** `androidx.security:security-crypto` (`EncryptedSharedPreferences`, `MasterKey`)
- **Desktop JVM:** OS keyring via `com.github.javakeyring`, or `Preferences` if the data is non-sensitive
- **Web:** `localStorage` is **not** secure. For tokens, use HttpOnly cookies set by your backend; for non-sensitive prefs `localStorage` is fine

### Encrypting data at rest

If you cache sensitive data in a local DB, encrypt the database:

- SQLDelight + SQLCipher (`net.zetetic:android-database-sqlcipher`)
- Realm has built-in encryption

## Authentication

- Use OAuth 2.0 / OIDC with PKCE — the only flow safe for native + web clients
- Short-lived access tokens (≤1h), refresh tokens stored in secure storage, rotation on each use
- Don't roll your own crypto. Use platform APIs or Ktor + a vetted OAuth library

## Input validation

- Validate every external input (network, deeplinks, file imports) at the boundary
- Use a sealed-class result type, not exceptions, for expected errors
- For deeplinks and intents (Android), declare an explicit allowlist of accepted hosts/paths

## Logging and analytics

- **Don't log PII** — names, emails, phone numbers, locations
- Don't log full request/response bodies in production
- Strip auth headers from any HTTP log
- If using crash reporting (Crashlytics, Sentry), enable PII scrubbing in the SDK and verify with a test crash

## Dependency hygiene

1. **Pin versions** — already done via `gradle/libs.versions.toml`
2. **Review every new dependency.** Check repo activity, maintainer reputation, and CVE history
3. **Renovate / Dependabot** — automate version-bump PRs but review them; don't auto-merge
4. **`./gradlew :composeApp:dependencies`** — review the full transitive tree periodically
5. **License compliance** — track licenses if you redistribute. The Compose-MP stack is mostly Apache 2.0 + MIT — friendly for commercial use, but verify each dependency

## Android specifics

### Manifest hardening

- `android:exported` — explicit on every component (Activity, Service, Receiver). Default is `false` from API 31; don't override to `true` unless required
- `android:allowBackup="true"` — currently enabled. If your app stores sensitive data, set to `false` or supply an `android:fullBackupContent` rule
- Remove `android:debuggable` overrides (it's automatic per build type)

### Network security config

Once on production, add `composeApp/src/androidMain/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

Reference it in the manifest:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... />
```

### Signing

- Use a release keystore stored outside the repo (e.g., `~/keys/`)
- Enable Play App Signing — Google holds the upload key; you keep the upload key
- Rotate your upload key annually if possible

## iOS specifics

### Code signing

- Use a Distribution certificate for App Store / Ad Hoc; a Development certificate for testing
- Enable Hardened Runtime + Library Validation in Capabilities
- App Transport Security is on by default — keep it on

### Privacy manifests

iOS 17+ requires `PrivacyInfo.xcprivacy` for any SDK that accesses user data. Add one when you start using analytics, crash reporting, or ad SDKs.

### Jailbreak / tamper detection

Useful for high-risk apps (banking, health). Options:

- iOS: detect `cydia://` URL handler, presence of Cydia Substrate, modified entitlements
- Don't roll your own — use a maintained library and treat detection as a *signal*, not a hard block

## Web specifics

- Set CSP (Content Security Policy) headers at your CDN/host:
  `default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'`
  (Wasm needs `'wasm-unsafe-eval'`)
- Set `X-Frame-Options: DENY` to prevent clickjacking
- Set `Strict-Transport-Security` (HSTS)
- For auth: HttpOnly + Secure + SameSite=Strict cookies; never put tokens in `localStorage`

## Desktop specifics

- Sign and notarize macOS apps for distribution outside the Mac App Store
- Sign Windows apps with an EV certificate to avoid SmartScreen warnings
- Don't write user data to system-wide locations; use OS-appropriate per-user directories (`~/Library/Application Support/<app>` on macOS, `%APPDATA%/<app>` on Windows)

## Threat model checklist before launch

- [ ] No secrets in git history (`git log --all -p | grep -i 'api_key\|password\|secret'`)
- [ ] All HTTP is HTTPS
- [ ] Auth tokens in platform secure storage
- [ ] R8 / minification enabled for Android release
- [ ] iOS app signed with Distribution cert and ATS enabled
- [ ] Web bundle served with CSP, HSTS, and X-Frame-Options
- [ ] Crash reporting strips PII
- [ ] Analytics scrubbed of personal data
- [ ] Privacy manifest (iOS 17+) declares all reasons-for-API-use
- [ ] Dependencies reviewed, no critical CVEs (`./gradlew :composeApp:dependencies` + a CVE scanner)

## Reporting vulnerabilities

When you fork this starter, add a `SECURITY.md` at the repo root with:

- Where to report (security@your-domain or a GitHub Security Advisory)
- Expected response time
- Coordinated disclosure policy
