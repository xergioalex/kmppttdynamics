---
name: release-web
description: Build and deploy the web bundle (Wasm preferred, JS as fallback)
---

# Skill: `/release-web`

Build the production web bundle and deploy it to a static host. Default to **Wasm**; JS only if you must support browsers without Wasm GC.

## Inputs to confirm

- **Target:** Wasm (preferred) or JS (legacy)
- **Host:** Cloudflare Pages, Netlify, Vercel, GitHub Pages, S3 + CloudFront, or another
- **URL path:** root domain (`https://app.example.com/`) or subpath (`https://example.com/app/`)
- **Cache headers:** confirm the host supports content-hashed asset caching

## Procedure

### 1. Build the production bundle

#### Wasm (preferred)

```bash
./gradlew clean
./gradlew :composeApp:wasmJsBrowserDistribution
# Output: composeApp/build/dist/wasmJs/productionExecutable/
```

#### JS (legacy fallback)

```bash
./gradlew :composeApp:jsBrowserDistribution
# Output: composeApp/build/dist/js/productionExecutable/
```

Inspect the output:

```bash
ls -lh composeApp/build/dist/wasmJs/productionExecutable/
```

Typical contents: `composeApp.js`, `composeApp.js.map`, `*.wasm`, `index.html`, `styles.css`, fonts, generated resources. The Wasm bundle is content-hashed for caching.

### 2. Subpath hosting

If hosting under a subpath (e.g., `https://example.com/app/`), edit `composeApp/src/webMain/resources/index.html` **before building**:

```html
<base href="/app/">
```

If you ship to multiple paths from the same artifact, set `<base>` dynamically with a small inline script that reads the deploy environment.

### 3. Smoke-test locally

```bash
cd composeApp/build/dist/wasmJs/productionExecutable
python3 -m http.server 8080
# Or: npx http-server -p 8080
```

Open `http://localhost:8080` in Chrome (Wasm GC required: 119+). Verify:

- App loads without console errors
- Compose UI renders
- Network requests succeed
- Resources (images, strings) display

### 4. Deploy

#### Cloudflare Pages

Connect the repo, set the build command to `./gradlew :composeApp:wasmJsBrowserDistribution`, and the output directory to `composeApp/build/dist/wasmJs/productionExecutable`. Cloudflare auto-deploys on push.

#### Netlify

`netlify.toml`:

```toml
[build]
  command = "./gradlew :composeApp:wasmJsBrowserDistribution"
  publish = "composeApp/build/dist/wasmJs/productionExecutable"

[[headers]]
  for = "/*.wasm"
  [headers.values]
    Content-Type = "application/wasm"
    Cache-Control = "public, max-age=31536000, immutable"

[[headers]]
  for = "/index.html"
  [headers.values]
    Cache-Control = "public, max-age=0, must-revalidate"
```

#### Vercel

`vercel.json`:

```json
{
  "buildCommand": "./gradlew :composeApp:wasmJsBrowserDistribution",
  "outputDirectory": "composeApp/build/dist/wasmJs/productionExecutable",
  "headers": [
    {
      "source": "/(.*).wasm",
      "headers": [
        { "key": "Content-Type", "value": "application/wasm" },
        { "key": "Cache-Control", "value": "public, max-age=31536000, immutable" }
      ]
    }
  ]
}
```

#### S3 + CloudFront

```bash
aws s3 sync composeApp/build/dist/wasmJs/productionExecutable s3://your-bucket/ --delete \
    --cache-control "public, max-age=31536000, immutable"
aws s3 cp composeApp/build/dist/wasmJs/productionExecutable/index.html s3://your-bucket/ \
    --cache-control "public, max-age=0, must-revalidate"
aws cloudfront create-invalidation --distribution-id <ID> --paths "/index.html"
```

Configure `application/wasm` MIME type in CloudFront if not auto-detected.

#### GitHub Pages

Push the build output to `gh-pages` branch, or use a GitHub Action:

```yaml
- run: ./gradlew :composeApp:wasmJsBrowserDistribution
- uses: peaceiris/actions-gh-pages@v3
  with:
    github_token: ${{ secrets.GITHUB_TOKEN }}
    publish_dir: composeApp/build/dist/wasmJs/productionExecutable
```

If your repo is `user/repo` and Pages serves at `user.github.io/repo/`, set `<base href="/repo/">` in `index.html`.

### 5. Cache headers (critical)

| Path | Cache-Control |
|---|---|
| `/index.html` | `public, max-age=0, must-revalidate` (or `no-cache`) |
| `*.wasm`, `*.js`, `*.css`, `/composeResources/*` | `public, max-age=31536000, immutable` |

Compose-MP content-hashes asset filenames; they're safe to cache forever. Only `index.html` references them by hash, so it must always be fresh.

### 6. Compression

Confirm your host serves Brotli (preferred) or gzip for `.wasm`, `.js`, `.css`. Most CDNs handle this automatically.

### 7. Security headers

Add at your host / CDN:

- `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Content-Security-Policy:` (start strict, loosen as needed)
  - Wasm needs `'wasm-unsafe-eval'` in `script-src`
  - Compose-MP inlines styles → include `'unsafe-inline'` in `style-src` or rework with hashed nonces

Example CSP:

```
default-src 'self';
script-src 'self' 'wasm-unsafe-eval';
style-src 'self' 'unsafe-inline';
img-src 'self' data:;
font-src 'self';
connect-src 'self' https://api.example.com;
```

### 8. Smoke-test the deployed site

- Open in Chrome, Firefox, Safari (latest stable)
- Run a Lighthouse audit — target 90+ on Performance, Accessibility, Best Practices, SEO
- Test on a slow 3G profile in DevTools — first paint should appear before 2s, app interactive before 5s

### 9. Tag and document

```bash
git tag web/v1.0.1
git push origin web/v1.0.1
```

Update [`docs/BUILD_DEPLOY.md`](../../docs/BUILD_DEPLOY.md) if the release process changed.

## Versioning the web bundle

There's no built-in version stamp. Add one to your build:

```kotlin
// composeApp/build.gradle.kts
val webVersion = providers.gradleProperty("webVersion").getOrElse(
    System.getenv("GIT_COMMIT")?.take(7) ?: "dev"
)

tasks.named("wasmJsBrowserDistribution").configure {
    doLast {
        val indexHtml = file("build/dist/wasmJs/productionExecutable/index.html")
        indexHtml.writeText(
            indexHtml.readText().replace(
                "</head>",
                "<meta name=\"version\" content=\"$webVersion\" /></head>"
            )
        )
    }
}
```

This stamps the git SHA into `index.html` for support / debugging.

## Pitfalls

1. **Wrong MIME type for `.wasm`** — Chrome refuses to compile if served with `application/octet-stream`. Most CDNs are correct; verify
2. **Long-cached `index.html`** — users get stale JS references and the app breaks. Always set short cache for HTML
3. **Missing `<base href>`** — subpath deployments load relative URLs from the wrong root. Set explicitly
4. **CORS on API calls** — your API must allow your web origin
5. **Wasm GC requirement** — the Wasm bundle won't run on Safari < 18.2, Firefox < 120, Chrome < 119. If you need older support, ship the JS bundle alongside

## Don't

- Cache `index.html` long-term
- Ship without compression (.wasm files compress 3-4×)
- Use the JS target by default — Wasm is faster and smaller
- Forget to invalidate CDN cache for `index.html` after deploys

## Do

- Use Wasm target by default
- Set immutable caching on hashed assets and short caching on `index.html`
- Run Lighthouse audits before announcing the release
- Tag the release in git
