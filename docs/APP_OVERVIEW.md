# Pereira Tech Talks Dynamics — App Overview

A realtime meetup engagement platform written **once in `commonMain`** and deployed to **Android, iOS, Desktop (JVM), Web (Wasm), and Web (JS)**. Built for the [Pereira Tech Talks](https://pereiratechtalks.com) community, designed as a generic Meetup Dynamics product.

## Mental model

```
App
  └── Meetups / Rooms          ← created by the host, joined by a code
        ├── Participants       ← realtime list, online presence
        ├── Chat + Announcements
        ├── Hand Raise queue
        ├── Q&A with upvotes
        ├── Polls
        ├── Raffles
        ├── Trivia (later)
        ├── Reactions (later)
        └── Live Activity Feed
```

Every dynamic is scoped by `meetup_id`. A single Supabase Realtime channel per table per meetup keeps every connected device in sync.

## Roles

| Role | What they can do |
|---|---|
| **Global admin** | Create meetups, archive any room. For the MVP this is just the project owner; auth gating arrives in M7. |
| **Room host** | The participant who joined as host (`role = 'host'`). Starts / pauses / ends the meetup; in later milestones moderates chat, manages raised hands, runs polls / raffles, draws winners. |
| **Participant** | Joins by code or by tapping a meetup. Can chat, raise hand, ask & upvote questions, vote in polls, enter raffles, react. |

Anonymous participation is allowed in the MVP — host-only enforcement is currently UI-side. Hardened RLS / Supabase Auth lands in M7.

## Milestones

| # | Scope | Status |
|---|---|---|
| **M1** | **Rooms + Participants + realtime foundation** | ✅ Implemented |
| M2 | Chat + announcements                           | ⏳ Planned |
| M3 | Raise hand + Q&A                               | ⏳ Planned |
| M4 | Polls                                          | ⏳ Planned |
| M5 | Raffles                                        | ⏳ Planned |
| M6 | Host dashboard polish, projection-friendly views | ⏳ Planned |
| M7+ | Trivia, reactions, leaderboard, QR check-in, Supabase Auth, hardened RLS, Edge Functions, analytics, history exports | ⏳ Planned |

### M1 — what works today

- Create meetup with auto-generated 6-char join code (alphabet excludes `0/O/1/I` for stage readability)
- List live meetups + past meetups on home
- Join by code or by tapping a meetup
- Pick a display name; optional "join as host" switch
- Room screen with realtime participant list (`is_online`, joined-at sort)
- Host controls: start (`live`) / pause / end
- Online + total participant counts
- Friendly "Supabase not configured" screen if `.env` is empty

### Source-set layout for M1

```
commonMain
  domain/        Meetup, MeetupParticipant, MeetupStatus, ParticipantRole
  supabase/      SupabaseClientProvider (lazy, BuildKonfig-driven)
  meetups/       MeetupRepository (REST + realtime channel for "meetups")
  participants/  ParticipantRepository (REST + realtime channel for "meetup_participants")
  settings/      AppSettings — theme + last display name (multiplatform-settings)
  ui/{home,create,join,room,theme,components}
```

Every realtime feed lives in `commonMain`. No platform-side cloning.

## KMP patterns demonstrated

| Pattern | Where it shows up |
|---|---|
| Shared domain models | `domain/Meetup.kt`, `domain/MeetupParticipant.kt` (kotlinx-serialization for Postgrest) |
| Shared UI | Every screen in `ui/` is `@Composable` in `commonMain` |
| Shared ViewModels | `*ViewModel.kt` extend `androidx.lifecycle.ViewModel` from `commonMain` |
| `expect`/`actual` | `Platform.kt` + per-platform actuals (kept minimal — most platform glue lives in each `main.kt`) |
| Multiplatform resources | `composeResources/` strings + `ptt_logo_*.png` accessible via `Res.string.*` / `Res.drawable.*` |
| Build-time config | BuildKonfig generates a `BuildConfig` Kotlin object from `.env` so commonMain can read SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY |
| Supabase Realtime in KMP | `MeetupRepository.observeAll()`, `ParticipantRepository.observe(meetupId)` — same flow shape will be reused for every future feature |

## Data model (M1 only)

Two tables drive M1:

```
meetups
  id           uuid pk
  title        text
  description  text?
  join_code    text unique
  status       text  -- draft | live | paused | ended | archived
  created_by   uuid?
  starts_at    timestamptz?
  ended_at     timestamptz?
  created_at   timestamptz

meetup_participants
  id            uuid pk
  meetup_id     uuid fk → meetups
  user_id       uuid?
  display_name  text
  role          text   -- host | participant | moderator
  is_online     boolean
  joined_at     timestamptz
  last_seen_at  timestamptz?
```

Both are added to the `supabase_realtime` publication via the migration. The full schema for later milestones (chat, hand raises, questions, polls, raffles, activity events) is also created up front in `supabase/migrations/001_init.sql` so the database is ready when each milestone lands.

## Realtime architecture

Every repository that powers a live UI follows the same shape:

```kotlin
fun observe(meetupId: String): Flow<List<X>> = flow {
    val channel = supabase.channel("x_$meetupId")
    val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "x"
        filter("meetup_id", FilterOperator.EQ, meetupId)
    }
    channel.subscribe()
    try {
        emit(fetch(meetupId))                        // initial REST snapshot
        changes.collect { emit(fetch(meetupId)) }    // refresh on every change
    } finally {
        withContext(NonCancellable) { channel.unsubscribe() }
    }
}
```

For M1 we re-fetch on each change rather than diff-applying. It's correct, simple, and fast enough for room sizes we expect. Diff-based updates can come later if we need them.

## What's deliberately NOT in M1

- **Chat / hand raise / Q&A / polls / raffles / trivia / reactions** — staged into M2–M5.
- **Persistent participant identity** — joining is anonymous; refreshing the app drops your participant row from the host's perspective.
- **Offline cache** — Supabase is the only source of truth. SQLDelight (which lived in the previous Todo iteration of this repo) is gone. An offline read cache is an M6+ follow-up.
- **Supabase Auth** — the publishable key is the only credential today. M7 layers auth and tightens RLS.
- **Hardened RLS** — current policies are permissive (anon can read/write anything). The migration calls this out with a `WARNING` block.
- **Raffle fairness guarantees** — when M5 lands, the draw should move into a SQL function or Edge Function; until then the host's client picks the winner.

## Want the long version?

- [Architecture](ARCHITECTURE.md) — source sets, expect/actual, Compose Multiplatform conventions
- [Technologies](TECHNOLOGIES.md) — full version catalog with rationale per dep
- [Standards](STANDARDS.md) — Kotlin / Compose conventions, naming, expect/actual rules
- [Platforms](PLATFORMS.md) — per-platform notes (Android, iOS, Desktop JVM, JS, Wasm)
- [Security](SECURITY.md) — secrets, RLS, what to tighten before production
