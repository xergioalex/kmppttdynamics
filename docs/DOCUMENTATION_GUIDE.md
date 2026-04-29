# Documentation Guide

When and how to update the docs that live in this repo. The goal is to keep `AGENTS.md` and `docs/` in sync with the code so future contributors (human or agent) can land productively without having to reverse-engineer your conventions.

## Documentation map

| File | Purpose | Update when… |
|---|---|---|
| `AGENTS.md` (root) | Single source of truth for AI agents — non-negotiable rules, quick command reference | Mandatory rules change, commands change, new patterns become canonical |
| `CLAUDE.md` | Symlink → `AGENTS.md` | Never edit directly |
| `README.md` | Human-facing intro, run instructions | The primary value proposition or run command changes |
| `docs/ARCHITECTURE.md` | Source sets, expect/actual, module layout | New source set, new target, new architectural pattern |
| `docs/TECHNOLOGIES.md` | Stack with versions and roles | Major dependency added/removed/bumped |
| `docs/STANDARDS.md` | Coding rules | A convention is decided or changes |
| `docs/DEVELOPMENT_COMMANDS.md` | Gradle command reference | A new task is wired or a workflow changes |
| `docs/TESTING_GUIDE.md` | Test framework, conventions | Test framework changes, new test layer added |
| `docs/PLATFORMS.md` | Per-target notes | New target added, platform-specific gotcha discovered |
| `docs/BUILD_DEPLOY.md` | Release pipeline per target | Signing / R8 / CI pipeline changes |
| `docs/I18N_GUIDE.md` | Localization workflow | Adding/removing a supported language |
| `docs/PERFORMANCE.md` | Optimization rules | A perf decision is made (e.g., R8 enabled, baseline profile) |
| `docs/REALTIME_PATTERNS.md` | Supabase Realtime conventions and gotchas | A new realtime feed pattern is established (e.g., the multi-channel-per-feature one for Trivia, the freshness-window one for one-shot animations) |
| `docs/IDENTITY_AND_AVATARS.md` | Cross-meetup identity, avatar rules, role visualization | Profile flow changes, new avatar surfaces, role / capability rules change |
| `docs/TRIVIA.md` | Kahoot-style live game (state machine, scoring, animations) | The trivia feature evolves (new sub-screen, new edge case, advance logic moves server-side) |
| `docs/MIGRATIONS.md` | Per-file SQL migration rationale | Every new migration |
| `docs/ACCESSIBILITY.md` | A11y rules | A11y conventions change |
| `docs/SECURITY.md` | Security baseline | Adding auth, secrets, secure storage, releasing to a store |
| `docs/AI_AGENT_ONBOARDING.md` | First-run flow for any AI agent | The onboarding path changes |
| `docs/AI_AGENT_COLLAB.md` | Multi-agent coordination | Agent roles or handoffs change |
| `docs/FORK_CUSTOMIZATION.md` | Rebrand checklist | A new fork-time concern is discovered |
| `.claude/README.md` | Skills & agents catalog | A skill or agent is added/removed |
| `.claude/skills/<name>.md` | Procedure for a slash command | The procedure changes |
| `.claude/agents/<name>.md` | Persona for a specialized agent | The persona changes |

## Triggers for updating

### Always require a doc update

- A new Gradle task or npm script
- A new `expect`/`actual` pair (mention in [Architecture](ARCHITECTURE.md) if it's an example pattern)
- A new dependency with non-trivial impact (Ktor, SQLDelight, a logger, etc.)
- A new platform target (e.g., `macosArm64()`)
- A new source set
- A change to the iOS framework name
- A change to `applicationId`, `namespace`, or bundle identifier
- Enabling R8 / signing / a CI pipeline

### Often require a doc update

- A non-obvious convention is decided ("ViewModels go in `feature/`")
- A new pattern is invented (e.g., a custom `LocalDispatchers` provider)
- A bug-prone area gets a workaround that future contributors should know

### Rarely require a doc update

- Bug fixes inside an existing pattern
- Routine version bumps within the same major version
- Style-only refactors

## How to write docs that age well

1. **Lead with the rule.** "All `actual` files end in `.android.kt` / `.ios.kt`" — not "We sometimes use a suffix convention…"
2. **Show one canonical example.** A code snippet that's the answer, not three options
3. **Explain *why* when it's non-obvious.** "Static iOS frameworks start faster — that's why `isStatic = true`"
4. **Cross-link.** Mentioning a topic? Link to the dedicated doc instead of duplicating content. Repetition leads to drift
5. **Date-tag exceptions.** "Until Compose Multiplatform 1.11 ships, we work around X" — easy to clean up later
6. **Keep the file under 500 lines.** Past that, split

## Cross-linking

Every doc that mentions something covered in detail elsewhere should link to it. Examples:

- AGENTS.md mentions hot reload → links to `docs/DEVELOPMENT_COMMANDS.md#desktop-jvm`
- `ARCHITECTURE.md` mentions a dependency → links to `TECHNOLOGIES.md`
- A skill in `.claude/skills/` references rules from `STANDARDS.md`

When you rename a doc, grep for its old name and update inbound links:

```bash
grep -rln "OLD_NAME.md" .
```

## Single source of truth

Each fact lives in **one** file. If you find yourself copying a paragraph, replace one copy with a link to the other.

Examples of "owned by":

- Coding conventions → `docs/STANDARDS.md`
- Gradle commands → `docs/DEVELOPMENT_COMMANDS.md`
- Stack versions → `docs/TECHNOLOGIES.md` (and pinned in `gradle/libs.versions.toml`)
- expect/actual rules → `docs/STANDARDS.md` + extended example in `docs/ARCHITECTURE.md`

## Writing for AI agents

`AGENTS.md` is read by AI assistants on every session. It must be:

1. **Scannable.** Tables, bullet lists, short paragraphs. Agents prioritize structured content
2. **Decisive.** "MUST", "DO", "DON'T" beat "consider" and "you might want"
3. **Concrete.** Include exact file paths, command invocations, and example code
4. **Self-contained at the section level.** An agent might quote one bullet — it should still be unambiguous

Skill files (`.claude/skills/<name>.md`) follow the same rules but are step-by-step procedures rather than reference docs. See [.claude/README.md](../.claude/README.md).

## Forking

When this project is forked into a new product, the **first commit on the fork** should:

1. Update [README.md](../README.md) with the new product name and purpose
2. Update [AGENTS.md](../AGENTS.md) Project Overview section
3. Walk through [Fork Customization](FORK_CUSTOMIZATION.md) and check every item
4. Audit `docs/` for content that no longer applies (e.g., if you drop iOS, remove iOS-specific sections)

## Reviewing doc PRs

When reviewing a PR that touches code, ask:

- Did this change add/remove a Gradle task, dependency, or pattern? → Doc update required
- Did this change touch a path mentioned in the docs? → Verify the docs still reflect reality
- Did the PR description say "I'll document this later"? → It rarely happens — block on docs in the same PR
