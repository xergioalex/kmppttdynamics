# Pereira Tech Talks Dynamics — App Overview

A realtime meetup engagement platform written **once in `commonMain`** and deployed to **Android, iOS, Desktop (JVM), Web (Wasm), and Web (JS)**. Built for the [Pereira Tech Talks](https://pereiratechtalks.com) community, designed as a generic Meetup Dynamics product.

## Mental model

```
App
  ├── Onboarding (display name + avatar)        ← shown once on first launch
  └── Meetups / Rooms                           ← created by the host, joined by a code
        ├── Members                             ← realtime list, online presence, avatar per row
        ├── Chat + Announcements                ← Discord-style with avatars
        ├── Hand Raise queue
        ├── Q&A with upvotes
        ├── Polls
        ├── Raffles                             ← avatar stack of entrants + winner reveal
        ├── Trivia (later)
        ├── Reactions (later)
        └── Live Activity Feed
```

Every dynamic is scoped by `meetup_id`. Each repository owns its own Supabase Realtime channel; the room mounts seven channels concurrently (`participants`, `chat`, `hands`, `questions`, `polls`, `raffles`, `app_users`) which is well within Supabase's free-tier limit of 200 concurrent connections per project. Channel topic names are uniquely suffixed per call — see [Realtime Patterns](REALTIME_PATTERNS.md).

## Identity model

- **Install client id** — a stable random hex string persisted in `multiplatform-settings` on every device. Generated once via `AppSettings.installClientId()` and reused everywhere. This is the spine of identity: it keys the global lobby presence, the cross-meetup profile, and the per-meetup participant row.
- **App user** — `public.app_users` row keyed by `client_id`. Holds the user's display name and **globally-unique avatar id** (1..132). Picked once during onboarding and reused for every meetup.
- **Meetup participant** — `public.meetup_participants` row keyed by `(meetup_id, client_id)` (partial unique index). At most one per device per meetup.

Read the full data flow in [Identity & avatars](IDENTITY_AND_AVATARS.md).

## Roles

| Role | What they can do |
|---|---|
| **Global admin** | Create meetups, archive any room. For the MVP this is just the project owner; auth gating arrives in M7. |
| **Room host** | The meetup creator; auto-assigned `role = 'host'` when they create the room. Starts / pauses / ends the meetup, promotes/demotes participants from the Members tab, hides chat messages, marks Q&A questions answered, runs polls / raffles, draws winners. |
| **Participant** | Joins by code or by tapping a meetup card. Can chat, raise hand, ask & upvote questions, vote in polls, enter raffles, react. |

Anonymous participation is allowed in the MVP — host-only enforcement is currently UI-side. Hardened RLS / Supabase Auth lands in M7.

## Milestones

| # | Scope | Status |
|---|---|---|
| **M1** | **Rooms + Participants + realtime foundation** | ✅ Implemented |
| **M2** | **Chat + announcements**                       | ✅ Implemented |
| **M3** | **Raise hand + Q&A (with upvotes)**            | ✅ Implemented |
| **M4** | **Polls (single-choice, anonymous default)**   | ✅ Implemented |
| **M5** | **Raffles (host-side draw + reveal)**          | ✅ Implemented |
| **M6** | **Cross-meetup profile (display name + unique avatar)** | ✅ Implemented |
| M7 | Host dashboard polish, projection-friendly views | ⏳ Planned |
| M8+ | Trivia, reactions, leaderboard, QR check-in, Supabase Auth, hardened RLS, Edge Functions, analytics, history exports | ⏳ Planned |

### What works today

- **Onboarding & profile**: first-launch screen picks a unique avatar (1 of 132 bundled) + a display name. Both are persisted locally and on the server. Tapping the home profile chip re-opens the picker for editing.
- **Avatar uniqueness, real-time**: `app_users.avatar_id` is a database-level UNIQUE column; the picker grid renders taken avatars locked with a 🔒 overlay and "Used by <name>" subtitle the moment another device claims one.
- **No-prompt joins**: tapping a meetup card auto-resolves the device's seat (server lookup by `client_id`, fallback to the local cache, fallback to a fresh `participants.join`). The old "pick your name" screen is gone — the profile follows the user into every room.
- **Avatars everywhere in the room**: Members, Chat (Discord-style row with avatar + name + bubble), Q&A (asker info row), Hand queue, Polls (creator info row), Raffles (overlapping `EntryAvatarStack` + 84 dp `WinnerReveal`).
- **Auto-host on create + Members management**: meetup creator joins as `HOST`; any host can promote / demote others. Re-entry preserves role because the `(meetup_id, client_id)` upsert path keeps the existing role.
- **App-wide presence counter on Home** via Supabase Realtime Presence on a single `app_lobby` channel keyed by the install-stable client id.
- **Room status** controls (start / pause / end), online + total participant counts, friendly status pills.
- **6-character join codes** (alphabet excludes `0/O/1/I` for stage readability).
- **Friendly "Supabase not configured" screen** if `.env` is empty at build time.

### Source-set layout

```
commonMain
  domain/         Meetup, MeetupParticipant, MeetupStatus, ParticipantRole,
                  AppUser, AppUserDraft,
                  ChatMessage, ChatType, ChatStatus,
                  RaisedHand, HandStatus,
                  Question, QuestionStatus, QuestionVote,
                  Poll, PollOption, PollVote, PollStatus,
                  Raffle, RaffleEntry, RaffleWinner, RaffleStatus
  supabase/       SupabaseClientProvider (lazy, BuildKonfig-driven)
                  RealtimeChannelNames (uniqueRealtimeTopic helper)
  appusers/       AppUserRepository — REST + realtime channel (no meetup scope)
  meetups/        MeetupRepository
  participants/   ParticipantRepository (find-then-update join, claim, role mgmt)
  chat/           ChatRepository
  handraise/      HandRepository
  qa/             QuestionRepository (with question_votes upvote handling)
  polls/          PollRepository (PollBoard snapshot, upsert vote)
  raffles/        RaffleRepository (RaffleBoard snapshot, host-side draw, upsert enter)
  presence/       GlobalPresenceTracker (Realtime Presence on `app_lobby`)
  settings/       AppSettings — theme + profile + per-meetup participantId cache + installClientId
  ui/{onboarding,home,create,room,room/tabs,theme,components}
```

The `join/` package is gone — joining a meetup no longer prompts the user; the home view model resolves the seat directly using the stored profile.

## KMP patterns demonstrated

| Pattern | Where it shows up |
|---|---|
| Shared domain models | `domain/Meetup.kt`, `domain/AppUser.kt`, `domain/MeetupParticipant.kt` (kotlinx-serialization for Postgrest, with `@EncodeDefault` on every field that has a Kotlin default — see [Standards](STANDARDS.md#serialization)) |
| Shared UI | Every screen in `ui/` is `@Composable` in `commonMain`, including the avatar picker grid |
| Shared ViewModels | `*ViewModel.kt` extend `androidx.lifecycle.ViewModel` from `commonMain` |
| `expect`/`actual` | `Platform.kt` + per-platform actuals (kept minimal — most platform glue lives in each `main.kt`) |
| Multiplatform resources | `composeResources/` strings + `ptt_logo_*.png` accessible via `Res.string.*` / `Res.drawable.*`. **Avatars** under `composeResources/files/avatars/<id>.png` (132 PNGs, 192×192, 3.5 MB total) read with `Res.readBytes("files/avatars/$id.png")` and decoded via `decodeToImageBitmap()`. |
| Build-time config | BuildKonfig generates a `BuildConfig` Kotlin object from `.env` so commonMain can read SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY |
| Supabase Realtime in KMP | All eight repositories follow the same `observe* / observeAll / observeBoard` template — see [Realtime Patterns](REALTIME_PATTERNS.md) |
| Supabase Realtime Presence | `GlobalPresenceTracker` — websocket-only presence, no Postgres table |
| Activity-survival of singletons | `PttApplication.container` (Android) so `MainActivity` recreations don't spawn duplicate `GlobalPresenceTracker` and inflate the lobby count |

## Data model (M1–M6)

```
profiles                              ← reserved for future Supabase Auth users
  id              uuid pk
  display_name    text
  avatar_url      text
  created_at      timestamptz

app_users                             ← M6: cross-meetup identity
  client_id       text pk             ← matches meetup_participants.client_id
  display_name    text
  avatar_id       int  unique         ← 1..132, dedup'd globally
  created_at      timestamptz
  updated_at      timestamptz

meetups
  id              uuid pk
  title           text
  description     text?
  join_code       text unique
  status          text   -- draft | live | paused | ended | archived
  created_by      uuid?
  starts_at       timestamptz?
  ended_at        timestamptz?
  created_at      timestamptz

meetup_participants
  id              uuid pk
  meetup_id       uuid fk → meetups
  user_id         uuid?
  client_id       text?              ← M4: install-stable id
  display_name    text
  role            text   -- host | participant | moderator
  is_online       boolean
  joined_at       timestamptz
  last_seen_at    timestamptz?
  -- partial unique (meetup_id, client_id) where client_id is not null
```

The full schema (chat, hand raises, questions, polls, raffles, activity events) is created in `supabase/migrations/001_init.sql` and evolves over `002`–`006`. See [Migrations](MIGRATIONS.md) for the full story per file.

## Realtime feeds

| Repository | Channel topic (before unique suffix) | Source table | meetup-scoped? |
|---|---|---|---|
| `MeetupRepository.observeAll()` | `home_meetups` | `meetups` | No |
| `AppUserRepository.observeAll()` | `app_users` | `app_users` | No |
| `ParticipantRepository.observe(meetupId)` | `participants_<meetupId>` | `meetup_participants` | Yes |
| `ChatRepository.observe(meetupId)` | `chat_<meetupId>` | `chat_messages` | Yes |
| `HandRepository.observe(meetupId)` | `hands_<meetupId>` | `raised_hands` | Yes |
| `QuestionRepository.observe(meetupId)` | `questions_<meetupId>` | `questions` (+ votes) | Yes |
| `PollRepository.observeBoard(meetupId)` | `polls_<meetupId>` | `polls` + `poll_options` + `poll_votes` | Yes |
| `RaffleRepository.observeBoard(meetupId)` | `raffles_<meetupId>` | `raffles` + entries + winners | Yes |
| `GlobalPresenceTracker` | `app_lobby` (static — Presence) | n/a (websocket) | No |

Every observer above (except presence) wraps its topic with `uniqueRealtimeTopic(base)` to avoid the shared-channel teardown bug — read [Realtime Patterns](REALTIME_PATTERNS.md) before adding a new one.

## What's deliberately NOT in M1–M6

- **Trivia / reactions / leaderboard** — staged for later milestones.
- **Offline cache** — Supabase is the only source of truth. SQLDelight (which lived in the previous Todo iteration of this repo) is gone. An offline read cache is a follow-up.
- **Supabase Auth** — the publishable key is the only credential today. M8 layers auth and tightens RLS.
- **Hardened RLS** — current policies are permissive (anon can read/write anything). The migration calls this out with a `WARNING` block. See [Security](SECURITY.md) for the production hardening checklist.
- **Raffle fairness guarantees** — when M5 lands, the draw should move into a SQL function or Edge Function; until then the host's client picks the winner.
- **Cross-device profile sync without an account** — your install id is unique per device. Two devices the same person uses get two `app_users` rows, two avatars. Auth is the way to merge them later.

## Want the long version?

- [Architecture](ARCHITECTURE.md) — source sets, expect/actual, Compose Multiplatform conventions
- [Identity & avatars](IDENTITY_AND_AVATARS.md) — the spine of the user model + the picker UX
- [Realtime patterns](REALTIME_PATTERNS.md) — the `observe*` template + the shared-channel gotcha
- [Migrations](MIGRATIONS.md) — what each SQL file does and why
- [Technologies](TECHNOLOGIES.md) — full version catalog with rationale per dep
- [Standards](STANDARDS.md) — Kotlin / Compose conventions, naming, expect/actual rules, serialization gotchas
- [Platforms](PLATFORMS.md) — per-platform notes (Android, iOS, Desktop JVM, JS, Wasm)
- [Security](SECURITY.md) — secrets, RLS, what to tighten before production
