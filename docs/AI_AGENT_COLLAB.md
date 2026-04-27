# AI Agent Collaboration

When multiple AI assistants share this codebase — sometimes in parallel, often across separate sessions — they need a contract so they don't step on each other's work.

## Source of truth

| Topic | File |
|---|---|
| Mandatory rules | [`AGENTS.md`](../AGENTS.md) |
| Coding conventions | [`docs/STANDARDS.md`](STANDARDS.md) |
| Skills (slash commands) | [`.claude/skills/`](../.claude/skills/) |
| Agents (specialized personas) | [`.claude/agents/`](../.claude/agents/) |
| Per-skill catalog | [`.claude/README.md`](../.claude/README.md) |

Every agent reads `AGENTS.md` first. When `AGENTS.md` changes, **all** agents inherit the new rule on their next session — no per-tool config needed.

## Agent roster

The repo defines specialized agents (see [`.claude/agents/`](../.claude/agents/)). Use them when their domain matches:

| Agent | Owns | Don't use for |
|---|---|---|
| `kmp-architect` | Source-set placement decisions, expect/actual boundaries, target list changes | Routine code edits |
| `compose-ui` | Composable layout, theming, recomposition optimization | Logic-only changes |
| `platform-bridge` | Implementing `actual`s for Android/iOS/JVM/JS/Wasm | Adding pure shared logic |
| `test-author` | Writing tests in `commonTest` and platform-specific test source sets | Production code |
| `dependency-auditor` | Reviewing `libs.versions.toml` updates and multiplatform compatibility | Application logic |
| `release-engineer` | R8, ProGuard, signing, packaging, CI | Day-to-day feature work |
| `doc-writer` | Keeping `AGENTS.md` and `docs/` synchronized with code | Code changes |

## Skill invocation

Skills are reusable procedures invoked by slash command. Same name, different prefix per host:

| Host | Prefix | Example |
|---|---|---|
| Claude Code | `/` | `/add-screen` |
| Cursor / Codex / Gemini | `#` | `#add-screen` |
| Plain text fallback | none | "run add-screen" |

When a command is invoked, the agent **must**:

1. Look up the skill in [`.claude/README.md`](../.claude/README.md)
2. Read the linked procedure file in `.claude/skills/<name>.md` end to end
3. Follow the steps exactly — the file IS the spec
4. If the procedure requires another agent (e.g., `release-engineer`), delegate explicitly

## Handoffs

Agents run sequentially in a single session. When a task spans multiple roles:

1. Document what's done (commit message, PR description, scratch notes in `tmp/`)
2. Identify the next role explicitly: "Next: `test-author` to add `commonTest` coverage"
3. The next agent reads the prior commit / notes, picks up from there

For Claude Code, this is the **subagent** pattern. For other hosts that don't support subagents, leave the handoff in the chat or in `tmp/handoffs/`.

## Parallel agents

Avoid concurrent edits to the same file. If parallelism is needed:

- Split work by directory or source set (`androidMain` vs `iosMain` is naturally orthogonal)
- Coordinate through `git` branches, not by hoping the merge resolves cleanly
- Each agent should run the full pre-commit checklist on its branch before merging

## Common conflicts

| Conflict | Resolution |
|---|---|
| Two agents bump the same dependency to different versions | Pick the higher version; rerun `./gradlew :composeApp:assemble :composeApp:allTests` |
| Two agents add unrelated `expect`/`actual` pairs | Both keep their work; one merges first, the other rebases |
| One agent edits `AGENTS.md`, another edits a doc that mirrors it | The `AGENTS.md` rule wins; update the doc to match |
| Two agents disagree on architecture (e.g., a screen's state lives in a `ViewModel` vs in the composable) | Default to ViewModel for screen-scoped state — it's the [STANDARDS.md](STANDARDS.md) rule. If the rule is wrong, fix the standard first, then the code |

## When in doubt

- **Defer to `AGENTS.md`** for non-negotiable rules
- **Defer to `STANDARDS.md`** for stylistic decisions
- **Ask the user** if neither covers the case — don't silently invent a new rule
- **Propose updating the docs** if you discover a gap. Add the new rule to `STANDARDS.md` (or the right doc) in the same PR

## Memory

If your host supports memory (Claude Code does, Cursor does, others vary):

- **Save** project-wide conventions only after the user has confirmed them
- **Don't save** ephemeral state like "currently working on feature X" — use `tmp/` instead
- **Verify before recommending** anything from memory; the code is the source of truth, memory may be stale
- For Claude Code specifically, see [`docs/AI_AGENT_ONBOARDING.md`](AI_AGENT_ONBOARDING.md) and the harness's auto-memory section

## Trust but verify

When taking work from another agent:

- Read the diff, don't trust the summary
- Re-run the full pre-commit checklist on your machine
- If tests fail, the work isn't done — even if the previous agent claimed otherwise

## Updating these rules

This file documents the **process** of agent collaboration. Update it when:

- A new agent role is added to `.claude/agents/`
- The skill invocation flow changes
- A new conflict pattern surfaces in real work and we want a written resolution

Mirror any update that affects rule precedence into `AGENTS.md`'s "Shared Agent Coordination" section.
