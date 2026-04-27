# AI Agent Onboarding

Whether you're Claude Code, Cursor, Codex, Gemini, GitHub Copilot, or any other AI coding assistant — read this once before touching the repo.

## In one minute

1. **Read [`AGENTS.md`](../AGENTS.md)** — the non-negotiable rules. Don't skip it.
2. **This is a Kotlin Multiplatform / Compose Multiplatform starter.** One shared `:composeApp` module produces apps for Android, iOS, Desktop JVM, Web JS, and Web Wasm.
3. **`commonMain` is where new code goes.** Drop into a platform source set only when forced.
4. **Versions live in `gradle/libs.versions.toml`** — never inline a version string.
5. **Build inner loop:** `./gradlew :composeApp:run` (Desktop hot reload) or `./gradlew :composeApp:jvmTest` (fast tests).
6. **`tmp/` is git-ignored scratch space** — put throw-away files there, never anywhere else.

## In ten minutes (recommended pre-task reading)

- [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) — source sets, expect/actual, project structure
- [`docs/STANDARDS.md`](STANDARDS.md) — naming, package layout, expect/actual rules
- [`docs/DEVELOPMENT_COMMANDS.md`](DEVELOPMENT_COMMANDS.md) — every Gradle command you'll need

## Before each task

1. **Identify which source set the change belongs in.** If you're not sure, default to `commonMain`. If the task touches platform APIs, identify the right platform source set
2. **Check if a skill matches.** See [`.claude/README.md`](../.claude/README.md). If yes, follow its procedure file step-by-step
3. **Plan briefly.** What files will you touch? What's the test strategy?
4. **Pick the inner loop.** Logic-only? `:composeApp:jvmTest`. UI work? `:composeApp:run` (Desktop hot reload)

## During the task

- **Edit only `AGENTS.md`** when updating Claude rules — `CLAUDE.md` is a symlink
- **Run the inner loop after every meaningful change.** Don't accumulate unverified changes
- **If you add an `expect`, immediately add the `actual` for every active target.** Missing actuals fail late
- **If you bump a dependency, only edit `gradle/libs.versions.toml`**

## Before claiming "done"

Run the pre-commit checklist from `AGENTS.md`:

- [ ] All code, comments, and identifiers in English
- [ ] `./gradlew :composeApp:assemble` succeeds
- [ ] `./gradlew :composeApp:allTests` passes
- [ ] If you added an `expect`, every active target has the matching `actual`
- [ ] Dependencies pinned in `gradle/libs.versions.toml`
- [ ] User-visible strings go through `stringResource(...)`
- [ ] No hardcoded colors (use `MaterialTheme.colorScheme.*`)
- [ ] No `local.properties` / `*.iml` / `xcuserdata/` staged
- [ ] Documentation updated for any architectural change
- [ ] Commit message in English (conventional format)

## Common patterns

### Adding shared logic

`composeApp/src/commonMain/kotlin/com/xergioalex/kmpstarter/<feature>/<File>.kt` — pure Kotlin, no platform deps. Add a test in the matching path under `commonTest/`.

### Adding a screen

A new composable in `commonMain`. Wire it into `App()` (or via navigation if you've adopted a router). Strings via `stringResource(Res.string.*)`. Use the `/add-screen` skill if available.

### Adding a platform-specific feature

1. Define an `expect` in `commonMain` with the smallest possible surface
2. Add `actual` in every platform source set: `androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`
3. Filename: `Foo.<platform>.kt`
4. Use the `/add-expect-actual` skill if available

### Adding a dependency

1. Add the version + library to `gradle/libs.versions.toml`
2. Reference it in the right `*.dependencies { ... }` block via `libs.<accessor>`
3. Run `./gradlew :composeApp:assemble` to confirm it resolves
4. Document the choice in `docs/TECHNOLOGIES.md`

### Bumping versions

Use the `/bump-deps` skill. Bump one library at a time, run tests, smoke each platform.

## Decision tree: where does this code go?

```
Is the code platform-specific (touches Android Context, UIKit, java.*, browser DOM)?
├── No  → commonMain
└── Yes → Can it be hidden behind an expect/actual contract that returns a platform-neutral result?
        ├── Yes → expect in commonMain, actual in the platform source set
        └── No  → Lives in the platform source set; called from commonMain via a small expect bridge

Is the code shared between web targets (JS + Wasm) but not other platforms?
└── webMain (already shared by the build)

Is the code an entry point (Activity, MainViewController, Window, ComposeViewport)?
└── The matching platform source set
```

## Tools you have

| Tool | Use |
|---|---|
| Read | Read code or docs to understand context |
| Bash | Run Gradle, git, find/grep |
| Edit / Write | Modify files |
| Grep / Find | Locate code by pattern |

Before bulk changes, read the relevant section of `AGENTS.md` and the doc it links to. Don't optimize for fewest tool calls at the cost of correctness.

## Things that look easy but aren't

- **Renaming the iOS framework** — `ComposeApp` is referenced from Swift; renaming requires editing both the Kotlin Gradle config and `iosApp/`
- **Removing a target** — code in that source set vanishes. Check for `expect`s that no longer have a needed `actual` to delete
- **Bumping Kotlin** — Compose Compiler plugin must match. See the [Compose Multiplatform compatibility table](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html)
- **Adding a "small" library** — make sure it has multiplatform variants for every active target

## Asking for clarification

If a task is ambiguous, ask **before** writing code. Common ambiguities:

- "Add a button" — Where? What does it do? Which platforms?
- "Translate the app" — To what language(s)? See [I18N Guide](I18N_GUIDE.md)
- "Optimize" — Bundle size? Frame rate? Cold start?

A 30-second clarification beats a 30-minute rewrite.

## Multi-agent coordination

If multiple agents collaborate, follow [`docs/AI_AGENT_COLLAB.md`](AI_AGENT_COLLAB.md).
