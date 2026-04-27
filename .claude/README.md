# Claude Code Skills & Agents — KMPStarter

This directory contains **skills** (slash-command procedures) and **agents** (specialized personas) tuned for Kotlin Multiplatform / Compose Multiplatform work in this repo.

> **For non-Claude hosts** (Cursor, Codex, Gemini, GitHub Copilot): the skill files in [`skills/`](skills/) are plain Markdown procedures. Invoke them by name (e.g., type `#add-screen` or "run add-screen") — the agent will read the matching file and follow it step-by-step.

## Layout

```
.claude/
├── README.md            # This file — skills + agents catalog
├── skills/              # One Markdown file per slash command
│   ├── add-screen.md
│   ├── add-expect-actual.md
│   ├── add-resource.md
│   ├── write-tests.md
│   ├── fix-build.md
│   ├── bump-deps.md
│   ├── add-platform-feature.md
│   ├── fork-rebrand.md
│   ├── release-android.md
│   ├── release-ios.md
│   ├── release-desktop.md
│   └── release-web.md
└── agents/              # One Markdown file per specialized agent
    ├── kmp-architect.md
    ├── compose-ui.md
    ├── platform-bridge.md
    ├── test-author.md
    ├── dependency-auditor.md
    ├── release-engineer.md
    └── doc-writer.md
```

## Skills (slash commands)

Reusable procedures invoked by slash command (or `#` in non-Claude hosts).

| Command | Purpose | File |
|---|---|---|
| `/add-screen` | Add a new shared Compose screen wired into `App()` | [skills/add-screen.md](skills/add-screen.md) |
| `/add-expect-actual` | Create an `expect` in `commonMain` plus `actual`s for every active target | [skills/add-expect-actual.md](skills/add-expect-actual.md) |
| `/add-resource` | Add a string / drawable / font to `composeResources` (and translations) | [skills/add-resource.md](skills/add-resource.md) |
| `/write-tests` | Author tests in `commonTest` (or platform-specific) for the current change | [skills/write-tests.md](skills/write-tests.md) |
| `/fix-build` | Diagnose and repair a failing Gradle build | [skills/fix-build.md](skills/fix-build.md) |
| `/bump-deps` | Update `gradle/libs.versions.toml` safely with verification | [skills/bump-deps.md](skills/bump-deps.md) |
| `/add-platform-feature` | Add a feature that needs a real platform API (network, storage, sensor) | [skills/add-platform-feature.md](skills/add-platform-feature.md) |
| `/fork-rebrand` | Walk a fresh fork through package, applicationId, namespace, bundle id, app name | [skills/fork-rebrand.md](skills/fork-rebrand.md) |
| `/release-android` | Produce a signed Android release | [skills/release-android.md](skills/release-android.md) |
| `/release-ios` | Archive and upload an iOS release | [skills/release-ios.md](skills/release-ios.md) |
| `/release-desktop` | Build a signed/notarized desktop installer | [skills/release-desktop.md](skills/release-desktop.md) |
| `/release-web` | Build and deploy the web bundle (Wasm preferred) | [skills/release-web.md](skills/release-web.md) |

### How to invoke

| Host | Prefix | Example |
|---|---|---|
| Claude Code | `/` (native) | `/add-screen` |
| OpenAI Codex | `#` | `#add-screen` |
| Cursor AI | `#` | `#add-screen` |
| Gemini / others | `#` | `#add-screen` |

When invoked, the agent **must**:

1. Look up the command name in this README
2. Read the linked skill file end-to-end
3. Follow its steps exactly — the file IS the spec
4. If the skill asks for a confirmation or input, pause and ask; don't improvise

## Agents (specialized personas)

Adopt the persona described in the file when the task matches.

| Agent | Domain | File |
|---|---|---|
| `kmp-architect` | Source-set placement, expect/actual boundaries, target list | [agents/kmp-architect.md](agents/kmp-architect.md) |
| `compose-ui` | Composable layout, theming, recomposition | [agents/compose-ui.md](agents/compose-ui.md) |
| `platform-bridge` | Implementing `actual`s for each platform | [agents/platform-bridge.md](agents/platform-bridge.md) |
| `test-author` | Writing and maintaining tests | [agents/test-author.md](agents/test-author.md) |
| `dependency-auditor` | `libs.versions.toml` reviews, multiplatform compatibility | [agents/dependency-auditor.md](agents/dependency-auditor.md) |
| `release-engineer` | R8 / signing / packaging / CI | [agents/release-engineer.md](agents/release-engineer.md) |
| `doc-writer` | Keeping `AGENTS.md` and `docs/` synchronized | [agents/doc-writer.md](agents/doc-writer.md) |

## Adding a new skill or agent

1. Create the Markdown file under `skills/<name>.md` or `agents/<name>.md`
2. Follow the structure of an existing file (frontmatter, then sections)
3. Add a row to the table above
4. Mention the new skill/agent in [`AGENTS.md`](../AGENTS.md) "Skills & Agents" section
5. Commit with `feat: add <name> skill` or `feat: add <name> agent`

## Conventions

- Skills are **procedural** — numbered steps, exact commands, file paths
- Agents are **persona-based** — what they care about, what they don't, how they decide
- Both must be self-contained at the file level — an agent reading only the skill should be able to execute it
- Cross-link to `docs/` rather than duplicating content
