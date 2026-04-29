# Trivia (Kahoot-style live quiz)

> The first feature that turns a meetup room into a real-time **game**.
> Read this in full before touching anything in `trivia/`,
> `ui/room/tabs/TriviaTab.kt`, or
> `supabase/migrations/007_trivia.sql`.

The host configures a quiz with N multiple-choice questions; the round
starts on a button press; every connected device runs a synchronized
countdown for each question; an answer-reveal flashes between
questions; after the last question every device runs a 10 s suspense
animation before the leaderboard is revealed (top 3 podium with
confetti, then the rest).

This document explains:

1. The 5-state quiz lifecycle and who fires each transition.
2. The data model and why every aggregation is server-side.
3. How time stays synchronized across devices without an NTP ping.
4. Each of the five sub-screens, with the visual decisions baked in.
5. The animation catalog (and why we centralize them in `TriviaTheme.kt`).
6. Edge cases and the common mistakes we already hit.

For a faster orientation see the bullet under "What works today" in
[App Overview](APP_OVERVIEW.md). For the SQL details see
[Migrations](MIGRATIONS.md#007--kahoot-style-trivia).

## Mental model

```
┌────────────────────┐                                      ┌─────────────────────┐
│ Host (one device)  │                                      │ Every other device  │
│ ────────────────── │                                      │ ─────────────────── │
│ • Edit questions   │  (status flips drive every screen)   │ • Watch the lobby   │
│ • Open lobby       │ ──────────────────────────────────▶  │ • Answer questions  │
│ • Start round      │                                      │ • Wait between Qs   │
│ • Skip / advance   │                                      │ • See leaderboard   │
└─────────┬──────────┘                                      └─────────┬───────────┘
          │                                                           │
          ▼                          (Supabase Realtime)              │
┌──────────────────────────────────────────────────────────────────────────────┐
│ trivia_quizzes.status                                                        │
│   draft ─▶ lobby ─▶ in_progress ─▶ calculating ─▶ finished                   │
│                                                                              │
│ trivia_quizzes.current_question_index   (server-authoritative)               │
│ trivia_quizzes.current_question_started_at                                   │
│ trivia_quizzes.calculating_started_at                                        │
└──────────────────────────────────────────────────────────────────────────────┘
```

Every UI screen is a pure function of the row's `status`. Clients **never** infer
state from local timers; the local timer is rendering only. This is the same
discipline `AGENTS.md §8 Server-Authoritative Game Timing` makes mandatory for
any future timed game.

## State machine

```
   ┌──────────────── DRAFT ────────────────┐
   │ Host edits questions, choices, time.   │
   └────────┬───────────────────────────────┘
            │ host: openLobby()  (only when ≥1 valid Q)
            ▼
   ┌──────────────── LOBBY ────────────────┐
   │ Participants gather, see avatar grid.  │
   │ Host can return to draft to keep       │
   │ editing, or start the round.           │
   └────────┬───────────────────────────────┘
            │ host: start()  (sets index=0,
            │ current_question_started_at=now())
            ▼
   ┌──────────────── IN_PROGRESS ──────────────┐
   │ Per-Q countdown ring + 4 colored buttons. │
   │ Local timer expires → host's device fires │
   │ advanceQuestion(expectedIndex).           │ ◀── auto re-fires every Q
   │ Co-hosts and the host can also tap Skip.  │
   └────────┬──────────────────────────────────┘
            │ index reaches last question
            ▼
   ┌──────────────── CALCULATING ──────────────┐
   │ 10 s suspense (BreathingHalo + dots).      │
   │ Every client locally times calculating_    │
   │ started_at + 10 s; whoever fires first     │
   │ wins, others no-op via WHERE guard.        │
   └────────┬──────────────────────────────────┘
            │ any client: finishCalculating()
            ▼
   ┌──────────────── FINISHED ─────────────────┐
   │ Podium 3-2-1 stagger + confetti + list.    │
   │ Host can "Play again" (reset to draft) or  │
   │ "New trivia" (create another quiz row).    │
   └────────────────────────────────────────────┘
```

### Who fires what

| Transition | Fired by | Why |
|---|---|---|
| `draft → lobby` | host | UX: only the meetup host edits the deck |
| `lobby → draft` (rare) | host | Returns to setup if the host wants to keep tuning |
| `lobby → in_progress` | host | Game starts at host discretion |
| `in_progress → in_progress` (next Q) | **host's device only**, automatically when the local timer hits 0 | Reduces network chatter; the WHERE-guarded UPDATE makes a doubled fire idempotent |
| `in_progress → in_progress` (skip early) | host (manual button) | Same path, host opts in |
| `in_progress → calculating` | host's device, when advancing past the last question | Same as above |
| `calculating → finished` | **any** device, after its local 10 s timer | The WHERE-guarded UPDATE collapses 50 fires into one accepted UPDATE |

### Idempotency: the WHERE-guarded UPDATE

Every state-changing call uses `current_question_index = expectedIndex AND status = '<expected>'` as part of the filter. A concurrent retry, two co-hosts pressing "Skip" at the same time, or a network-blip retry collapses to **one** winning row change.

Example from `TriviaRepository.advanceQuestion`:

```kotlin
supabase.from(QUIZZES).update({
    set("current_question_index", expectedIndex + 1)
    set("current_question_started_at", now)
}) {
    filter {
        eq("id", quizId)
        eq("current_question_index", expectedIndex)              // ← guard
        eq("status", TriviaStatus.IN_PROGRESS.name.lowercase())   // ← guard
    }
}
```

## Data model

Five entities live in `supabase/migrations/007_trivia.sql`. All four tables are added to `supabase_realtime`; the view inherits SELECT via a separate `grant`.

| Table / View | Role | Key constraint |
|---|---|---|
| `trivia_quizzes` | Quiz metadata + lifecycle pointers | — |
| `trivia_questions` | Per-question prompt + per-Q timer override | `UNIQUE (quiz_id, position)` |
| `trivia_choices` | 4 colored options per question | `UNIQUE (question_id, position)` |
| `trivia_answers` | One row per (device, question) | `UNIQUE (question_id, client_id)` ← idempotency |
| `trivia_leaderboard` (view) | Aggregate `(quiz, client)` joined to `app_users` | — |

### `trivia_quizzes`

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid pk` | |
| `meetup_id` | `uuid fk meetups(id) on delete cascade` | |
| `title` | `text` | "Round 1", etc. |
| `status` | `text` | `draft` / `lobby` / `in_progress` / `calculating` / `finished` |
| `default_seconds_per_question` | `int` | UI default; per-Q overrides win |
| `current_question_index` | `int?` | 0-based; null in draft / lobby |
| `current_question_started_at` | `timestamptz?` | **Source of truth for the countdown** |
| `calculating_started_at` | `timestamptz?` | Drives the 10 s suspense screen |
| `created_by_client_id` | `text?` | Audit only — control is `role=host` |
| `created_at` / `started_at` / `finished_at` | `timestamptz?` | |

### `trivia_answers`

```sql
create table public.trivia_answers (
    id              uuid primary key default gen_random_uuid(),
    quiz_id         uuid not null references public.trivia_quizzes(id) on delete cascade,
    question_id     uuid not null references public.trivia_questions(id) on delete cascade,
    participant_id  uuid not null references public.meetup_participants(id) on delete cascade,
    client_id       text not null,                                          -- joins to app_users
    choice_id       uuid not null references public.trivia_choices(id) on delete cascade,
    is_correct      boolean not null default false,                         -- filled by trigger
    response_ms     int    not null default 0,                              -- filled by trigger
    points_awarded  int    not null default 0,                              -- filled by trigger
    answered_at     timestamptz not null default now(),
    unique(question_id, client_id)
);
```

The client only sends `(quiz_id, question_id, participant_id, client_id, choice_id)`. The trigger fills the rest.

### `trivia_leaderboard` view

```sql
create view public.trivia_leaderboard as
select
    ta.quiz_id, ta.client_id,
    coalesce(au.display_name, 'Anon') as display_name,
    au.avatar_id,
    coalesce(sum(ta.points_awarded), 0)::int             as total_points,
    count(*) filter (where ta.is_correct)::int            as correct_count,
    count(*)::int                                         as answered_count,
    coalesce(avg(ta.response_ms) filter (where ta.is_correct), 0)::int as avg_response_ms
from public.trivia_answers ta
left join public.app_users au on au.client_id = ta.client_id
group by ta.quiz_id, ta.client_id, au.display_name, au.avatar_id;
```

The client-side sort is `total_points DESC, avg_response_ms ASC, display_name ASC` for full determinism.

## Server-side scoring

`trivia_compute_answer_score` is a `BEFORE INSERT` trigger on `trivia_answers`. It owns:

1. **Correctness lookup** — reads `trivia_choices.is_correct` for the chosen `choice_id`.
2. **Elapsed-time computation** — `now() - current_question_started_at`, clamped to the question window.
3. **Late-insert guard** — if `q.position ≠ tq.current_question_index` (the host already moved on), elapsed_ms is set to the full window so the score caps at 500 (correct) or 0 (wrong). Prevents a network race from gifting +1 000 pts.
4. **Score formula** — Kahoot-style: `500 + 500 * (1 - elapsed_ms / total_ms)` for correct, 0 for wrong.

Because the trigger is the **only** place these values are written, a malicious client sending `points_awarded: 999999` is simply ignored — the `BEFORE INSERT` overwrites the field before the row is committed.

## Real-time channels

Three flows per quiz, each filtered as narrowly as possible. Channel names use `uniqueRealtimeTopic(base)` per `AGENTS.md` rule (see [Realtime patterns](REALTIME_PATTERNS.md)).

| Flow | Source table | Filter | Used by |
|---|---|---|---|
| `observeBoard(meetupId)` | `trivia_quizzes` (filtered by meetup) + `trivia_questions` + `trivia_choices` | `meetup_id` on the quiz table only | Every screen — drives the status router |
| `observeAnswers(quizId)` | `trivia_answers` | `quiz_id` | `QuestionScreen` (host's "X / N answered" counter) |
| `observeLeaderboard(quizId)` | `trivia_answers` (re-fetches the view on every change) | `quiz_id` | `LeaderboardScreen` |

Why three feeds and not one combined? Cardinality: the board changes in setup mostly; answers fire dozens of times during the round; the leaderboard is read-after-write of the answers table. Splitting them by lifetime keeps each subscription's listener load minimal.

## Time synchronization

The trigger and the UI both rely on `current_question_started_at` (a `timestamptz` set when the host advances). On every client:

```kotlin
val deadline = startedAt + secondsToAnswer.seconds
val remaining = (deadline - Clock.System.now()).coerceAtLeast(Duration.ZERO)
```

Devices with clocks slightly off see a slightly-shorter or -longer countdown — but the **server timestamp is the source of truth**. Even if a client's clock is +5 s ahead, the trigger still computes `response_ms` from `now() - q_started_at` on the server. So scoring is unaffected.

The 15 s default per question gives plenty of headroom for typical NTP skew (±2 s). We deliberately did **not** add an NTP ping — it would be premature optimization for a community demo.

## The five sub-screens

Routed by `quiz.status` inside `TriviaTab.kt`:

| Status | File | Who sees what |
|---|---|---|
| no quiz | `TriviaTab.kt` (inline `EmptyState`) | host: "Create trivia" button. Others: "Host hasn't set up yet" |
| `draft` | `trivia/HostSetupScreen.kt` | host: editor cards + dialog. Others: empty hint |
| `lobby` | `trivia/LobbyScreen.kt` | everyone: avatar grid; host: Start + Edit |
| `in_progress` | `trivia/QuestionScreen.kt` | everyone: countdown ring + 4 buttons + reveal overlay |
| `calculating` | `trivia/CalculatingScreen.kt` | everyone: 10 s halo + dots animation |
| `finished` | `trivia/LeaderboardScreen.kt` | everyone: podium + list; host: Play again / New trivia |

### `HostSetupScreen`

Cards per question with the four colored choices preview, an "Edit" / "Delete" pair, and a sticky "Open lobby" button at the bottom. The Open-lobby button is **disabled** unless every question has a non-blank prompt, four non-blank labels, and exactly one correct answer marked. Validation lives in `HostSetupScreen.canOpenLobby` so the bottom-of-screen helper text explains why the button is grey.

The editor opens in a Material `AlertDialog`, which keeps the question list scannable when the host is reordering 10+ questions. The dialog uses `mutableStateListOf<String>` for the four labels so a single recompose covers any field edit.

### `LobbyScreen`

Title row centered + "5 questions · ~15 s each" subtitle + flex grid of online participants (using `FlowRow` so Wasm/JS layouts stay correct). Host bottom bar offers **Edit questions** (returns to draft) and **Start trivia**. The Start button is disabled if the quiz has zero questions — `lobby` is reachable from `draft` only when the host opened the lobby with a valid deck, but the guard catches the rare race where a question is deleted in another tab between "Open lobby" and "Start".

### `QuestionScreen`

The most animated screen. Layout:

```
┌────────────────────────────────────────────┐
│ Question 2 / 5                  [⊙ 12s]    │ ← CountdownRing
├────────────────────────────────────────────┤
│  ¿Cuál es la capital de Francia?            │ ← prompt card (secondaryContainer)
├────────────────────────────────────────────┤
│ 🔺 Madrid                                   │
│ 🔷 Paris                            ◯       │ ← user's pick (when locked)
│ 🟡 London                                   │
│ 🟢 Berlin                                   │
├────────────────────────────────────────────┤
│ Locked in — waiting for the others…   [Skip]│
│                                3/5 answered │
└────────────────────────────────────────────┘
```

After timer-zero, an **answer reveal banner** flashes for `TriviaTiming.ANSWER_REVEAL_MS` (1.8 s). It tells the user "+850 pts" or "Better luck next round" or "Time's up!". The host's device then fires `onAdvance(index)`. WHERE-guarded UPDATE means even if every device tried, only one would land.

### `CalculatingScreen`

Pure visual delay tied to `calculating_started_at + TriviaTiming.CALCULATING_MS` (10 s). Three dots breathing in stagger + a halo behind a sparkle emoji. When local timer runs out, **any** device fires `finishCalculating(quizId)` with a `WHERE status = 'calculating'` guard so concurrent fires no-op.

### `LeaderboardScreen`

Stagger reveal of the podium (3rd → 2nd → 1st with 150 ms gaps between animations) + custom `ConfettiOverlay` drawn behind the podium with `Modifier.drawBehind`. 36 particles with random offsets and a fixed seed (`Random(42)`) so the constellation stays stable across recompositions instead of jittering frame-to-frame.

The list below the podium uses a regular `LazyColumn` for everyone outside the top 3. Host buttons:

- **Play again** — calls `TriviaRepository.reset(quizId)`. Wipes `trivia_answers` for the quiz, sets `status=draft`, clears the lifecycle pointers. Questions and choices stay so the host doesn't have to rebuild the deck.
- **New trivia** — creates a brand-new `trivia_quizzes` row. The board's `active` getter now returns the new (draft) quiz so the screen routes back to setup.

## Animation catalog

Centralized in `ui/room/tabs/trivia/TriviaTheme.kt` so visual feel can be tuned in one place.

| Component | Animation | API |
|---|---|---|
| `QuestionScreen.CountdownRing` | Arc that drains; pulse in last `LAST_SECONDS_PULSE` (3) seconds | `Canvas` + `animateFloatAsState` + `rememberInfiniteTransition.animateFloat` |
| `QuestionScreen.RevealBanner` | Scale-in + fade | `AnimatedVisibility(scaleIn + fadeIn)` |
| `CalculatingScreen.BreathingHalo` | Scale + alpha breathing | `rememberInfiniteTransition` (1 400 ms reverse) |
| `CalculatingScreen.BreathingDots` | Triangle-wave stagger across 3 dots | Single `phase` float; per-dot offset |
| `LeaderboardScreen.Podium` | 3rd → 2nd → 1st reveal | `Animatable` + `LaunchedEffect` |
| `LeaderboardScreen.ConfettiOverlay` | 36 particles, fixed-seed | `drawBehind` + `infiniteTransition.animateFloat` |

The 4-color palette is **intentionally hardcoded** in `TriviaPalette` — these are the game's identity (the same vivid red/blue/yellow/green Kahoot uses) and remapping them through `colorScheme.*` would collapse two of them in some palettes and break the "tap the colored button" mechanic. Project rule "DON'T hardcode colors" gets a documented exception here. To compensate for color-blindness, every choice also carries a unicode shape (▲ ◆ ● ■).

## Edge cases

| Case | Behaviour |
|---|---|
| User joins meetup mid-round | Sees the current Q with a remaining countdown. Past Qs aren't backfilled; they score 0 by absence. |
| User taps a choice 1 ms before timer-zero, insert lands AFTER advance | Trigger detects `q.position ≠ current_question_index`, clamps `response_ms` to full window: 500 pts if correct, 0 if wrong. Never +1 000. |
| User switches away to another tab and back during a Q | `QuestionScreen` re-mounts; `LaunchedEffect(quiz.id, index)` re-subscribes. The local timer reads from `currentQuestionStartedAt` so the visible countdown is correct. The user's already-recorded answer (if any) is restored from the `observeAnswers` flow keyed by `client_id`. |
| Host navigates away after starting the round | The host's device fires advance only while `QuestionScreen` is mounted. If host backs out of the room, the round hangs at the current question until they return. (v2: move advance to a Postgres `pg_cron` or Edge Function for resilience.) |
| Two co-hosts both press "Skip" | Both fire `advanceQuestion`. Only the first lands; the second's WHERE-guard makes it a no-op. The realtime feed delivers one transition. |
| User taps a choice twice (finger bounced) | `(question_id, client_id) UNIQUE` rejects the second. Client uses `upsert(ignoreDuplicates=true)` so it's a silent no-op, never a 409. |
| Host deletes the quiz mid-round | CASCADE wipes everything. The board's `active` flips to null; the screen routes to the empty state. |
| Network blip while submitting an answer | The client retry path uses the same idempotent upsert; trigger still computes `response_ms` correctly because the retry's `now()` is a few hundred ms later — small score penalty on a flaky connection. |

## Common mistakes to avoid

1. **Driving lifecycle off the local timer.** The local timer is *visual*. Every transition keys off the row's `status`, `current_question_index`, or `calculating_started_at`. Anything else lets a slow phone drift out of sync with the server.
2. **Computing scores client-side.** The trigger is the single source of truth. A client can't even cheat by sending `points_awarded` — `BEFORE INSERT` overwrites the field.
3. **Calling `advanceQuestion` from every device.** It's idempotent thanks to the WHERE guard, but firing 50 UPDATEs per Q for nothing wastes the realtime feed. Restrict to the host.
4. **Using `MaterialTheme.colorScheme.*` for the four choice buttons.** They'd collapse into similar colors in some themes; the `TriviaPalette` constants exist for a reason.
5. **Forgetting `unsubscribe()` in `finally` blocks** in any new flow you add (`AGENTS.md §16`). All three trivia flows already do it.
6. **Calling `rememberInfiniteTransition` conditionally.** Composables (including `animateFloat` extensions) must be invoked unconditionally so Compose's slot table stays stable. Compute the value, gate the *consumption* of the value.
7. **Reusing a static channel name.** `uniqueRealtimeTopic("trivia_$id")` per call, always. (See [Realtime patterns §1](REALTIME_PATTERNS.md#1--why-the-channel-name-must-be-unique-per-call).)

## Where to look in the code

| Concern | File |
|---|---|
| Data model + drafts | `composeApp/src/commonMain/kotlin/com/xergioalex/kmppttdynamics/trivia/TriviaModels.kt` |
| Repository + realtime flows | `composeApp/.../trivia/TriviaRepository.kt` |
| Status router | `composeApp/.../ui/room/tabs/TriviaTab.kt` |
| Host setup editor | `composeApp/.../ui/room/tabs/trivia/HostSetupScreen.kt` |
| Lobby grid | `composeApp/.../ui/room/tabs/trivia/LobbyScreen.kt` |
| Question + countdown ring + 4 buttons | `composeApp/.../ui/room/tabs/trivia/QuestionScreen.kt` |
| Suspense screen | `composeApp/.../ui/room/tabs/trivia/CalculatingScreen.kt` |
| Podium + confetti | `composeApp/.../ui/room/tabs/trivia/LeaderboardScreen.kt` |
| Palette + animation timing | `composeApp/.../ui/room/tabs/trivia/TriviaTheme.kt` |
| Schema + scoring trigger + view | `supabase/migrations/007_trivia.sql` |
| AppContainer wiring | `composeApp/.../AppContainer.kt` (search `trivia`) |
| RoomTab enum + tab strip | `composeApp/.../ui/room/RoomScreen.kt` (search `RoomTab.TRIVIA`) |
| i18n strings | `composeResources/values/strings.xml` + `values-es/strings.xml` (search `trivia_`) |

## Future work

Not in scope for the current implementation; tracked in [App Overview](APP_OVERVIEW.md#whats-deliberately-not-in-m1-m7):

- **Question media** (images / audio) — the `trivia_questions` schema can grow a `media_url` column without breaking the trigger or view.
- **Pre-saved quiz library** — the data model already supports multiple quizzes per meetup; a "duplicate quiz" action would be ~10 lines.
- **Auto-advance on host disconnect** — move the advance into a Postgres `pg_cron` or Edge Function so a missing host doesn't hang the round. Today it just waits for them to come back.
- **Multi-correct questions** — the schema doesn't enforce one-correct, only the UI does. A flag on `trivia_questions` plus a tweak to the scoring trigger would unlock this.
