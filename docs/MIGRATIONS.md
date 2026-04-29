# Database migrations

Every SQL file under `supabase/migrations/` is **idempotent** and
**applied in lexical order** by `./scripts/supabase_apply.sh`. Re-running
the script on an already-migrated database is a safe no-op for every
file except the one-shot data backfills (`002`, `003`, `005`), which
are still safe to re-apply because they short-circuit when their
target rows are already in the desired state.

This page walks through what each migration does, why it exists, and
the bug it prevents from coming back.

## Order of execution

```
supabase/migrations/
├── 001_init.sql                 # full schema + permissive RLS + realtime publication
├── 002_backfill_status.sql      # one-shot fix: set draft polls/raffles to open
├── 003_dedupe_participants.sql  # one-shot fix: collapse duplicate participants
├── 004_add_client_id.sql        # adds `meetup_participants.client_id` + unique index
├── 005_reset_data.sql           # wipe room data after the client_id refactor
├── 006_app_users.sql            # cross-meetup profile (display name + unique avatar)
├── 007_trivia.sql               # Kahoot-style trivia: 4 tables + view + scoring trigger
├── 008_trivia_entries.sql       # opt-in enrollment for trivia (mirrors raffle_entries)
└── 009_trivia_question_types.sql # adds boolean / multiple / numeric question kinds + per-type trigger
```

Run them all by setting up `.env` and executing:

```bash
./scripts/supabase_apply.sh
```

## 001 — initial schema

Creates every table the app uses (`meetups`, `meetup_participants`,
`chat_messages`, `raised_hands`, `questions`, `question_votes`,
`polls`, `poll_options`, `poll_votes`, `raffles`, `raffle_entries`,
`raffle_winners`, `activity_events`, `profiles`).

Notable design choices:

- All FKs to `meetups` use `on delete cascade` — wiping a meetup
  cleans every child table in one statement.
- `questions.upvotes_count` has a trigger that keeps it in sync with
  inserts and deletes on `question_votes`.
- All tables are added to the `supabase_realtime` publication via a
  loop that swallows `duplicate_object` errors so re-runs are safe.
- RLS is enabled with **permissive** policies (`using (true)` for
  every CRUD operation). This is fine for the MVP because
  participation is anonymous; production must tighten host-only
  actions. See [SECURITY](SECURITY.md) for the production hardening
  checklist.
- Default privileges are set so future tables in `public` automatically
  inherit `select/insert/update/delete` for `anon` and
  `authenticated`. Tables created via direct SQL otherwise return
  Postgres error 42501 ("permission denied") **before** RLS even gets
  to evaluate.

## 002 — backfill draft → open for polls and raffles

Before `@EncodeDefault(EncodeDefault.Mode.ALWAYS)` was added to
`PollDraft.status` and `RaffleDraft.status`, `kotlinx.serialization`
silently dropped the field from the JSON payload (its default policy is
`encodeDefaults = false`). The Postgres column default `'draft'` would
win, leaving every newly created poll / raffle stuck in Draft and
therefore unvotable / unenterable.

This migration upgrades any such rows to `'open'`:

```sql
update public.polls    set status = 'open', opened_at = coalesce(opened_at, now()) where status = 'draft';
update public.raffles  set status = 'open', opened_at = coalesce(opened_at, now()) where status = 'draft';
```

The Milestone-1 UX has no save-as-draft flow, so converting every
existing draft to open is safe. Once `@EncodeDefault` is in place, no
new draft rows are created from the client.

## 003 — dedupe participants

Until `HomeViewModel.onEnterMeetup` learned to distinguish
"participant row was deleted" from "transient network error", every
network blip during room re-entry routed the user back through the
join screen, which inserted a brand-new participant row. Result: rooms
filled with `XergioAleX (host)`, `XergioAleX (host)`, `Katherine`,
`Katherine`, `Katherine` — same device, same person, N rows.

This migration keeps the **oldest** row per `(meetup_id,
display_name)` and deletes the rest:

```sql
with ranked as (
    select id, meetup_id, display_name,
           row_number() over (
               partition by meetup_id, display_name
               order by joined_at asc, id asc
           ) as rn
    from public.meetup_participants
)
delete from public.meetup_participants p
 using ranked r
 where p.id = r.id and r.rn > 1;
```

We keep the oldest row because (a) any cached `participantId` on a
device most likely points to it, and (b) it tends to be the row with
`role = host` for the meetup creator.

## 004 — add `client_id` to participants

Adds the install-stable identifier column to `meetup_participants`
and creates a partial unique index on `(meetup_id, client_id)`:

```sql
alter table public.meetup_participants
    add column if not exists client_id text;

create unique index if not exists meetup_participants_meetup_client_unique
    on public.meetup_participants (meetup_id, client_id)
    where client_id is not null;
```

Why a **partial** index? Pre-migration rows still have `client_id =
NULL` and don't conflict with anything. New rows from clients that have
been upgraded to the `client_id`-aware build land with a non-null
value, and the unique index prevents the same install from creating
two rows in the same meetup — even when the local cache is empty
(fresh install) or stale (a developer cleared app data).

Pre-migration rows can be **claimed** by a device via
`ParticipantRepository.claim(participantId, clientId)` when the local
`AppSettings.participantIdFor(meetupId)` cache points to them; that
update fills in `client_id` so `findByClientId(...)` finds them on the
next entry without needing the cache.

## 005 — reset room data

After the `client_id` refactor, the easiest path forward was to wipe
all `meetup_participants` (and via cascade every chat message, hand,
question, poll, raffle, etc.) so testers could start clean:

```sql
delete from public.meetups;
```

The schema and migrations stay; only the data is wiped. The `meetups`
delete cascades through every child table. Re-running this on an
already-empty database is a free no-op.

You can re-apply this whenever you want to start fresh in a dev
environment — every other migration is idempotent so the schema
survives.

## 006 — app_users

Introduces the **cross-meetup profile** (display name + avatar id)
keyed by `client_id`:

```sql
create table public.app_users (
    client_id    text primary key,
    display_name text not null,
    avatar_id    int  not null unique,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
```

The `avatar_id unique` constraint is the database enforcement of the
"no two users may share an avatar" rule the picker shows
optimistically. Realtime updates on this table flow into the avatar
picker so taken avatars lock the moment another device claims one.

The migration also:

- Adds an `app_users_avatar_idx` index on `avatar_id` (the unique
  constraint already implies one but the explicit index documents
  intent).
- Adds the table to the `supabase_realtime` publication.
- Creates permissive RLS policies (idempotent — drops + creates).

See [Identity & avatars](IDENTITY_AND_AVATARS.md) for the full data
flow and how the picker uses this table.

## 007 — Kahoot-style trivia

Adds the **first server-authoritative live game** to the app: four
tables, one view, and one BEFORE-INSERT trigger that owns scoring.

```
trivia_quizzes      ← lifecycle row (draft / lobby / in_progress / calculating / finished)
   │
   ├── trivia_questions       ← N per quiz, ordered by `position`
   │      │
   │      └── trivia_choices  ← exactly 4 per question (Kahoot-style)
   │
   └── trivia_answers         ← (question_id, client_id) UNIQUE
                                  ↑ idempotent retries; client only sends
                                    (quiz, question, choice, participant);
                                    BEFORE INSERT trigger fills is_correct,
                                    response_ms, points_awarded.

trivia_leaderboard (view)     ← aggregates trivia_answers per (quiz, client),
                                joined to app_users for display name + avatar.
```

Notable design choices:

- **`current_question_started_at`** on `trivia_quizzes` is the single
  source of truth for every device's countdown timer. Local clocks
  decide rendering only; the trigger reads `now() - current_question_
  started_at` server-side to compute `response_ms`.
- **Server-side scoring** lives in `trivia_compute_answer_score`
  (BEFORE INSERT). The client only sends the chosen `choice_id`; the
  trigger fills correctness, response time, and Kahoot's
  `500 + 500 * (1 - elapsed/total)` formula. Sending pre-computed
  `points_awarded` from the client is silently overwritten.
- **Late-insert guard**: if the host already advanced past a question
  by the time an answer arrives (the user tapped at t = 0 and the
  network race lost), the trigger compares
  `q.position` against `tq.current_question_index` and clamps
  `response_ms` to the full window — score caps at 500 (correct) or
  0 (wrong), never the +1 000 instant-correct bonus. Prevents the
  network-race scoring exploit.
- **WHERE-guarded UPDATEs** on `current_question_index` so two co-host
  clicks (or a retry storm) collapse to one winning row change.
- The `trivia_leaderboard` view is **granted SELECT** to `anon`,
  `authenticated` explicitly. Views don't inherit the bulk grant from
  `001` because they're created after the schema-level grant runs.
- All four tables are added to `supabase_realtime`. The view isn't —
  the client subscribes to `trivia_answers` and re-fetches the
  leaderboard on each change instead.

For the full reasoning, the per-screen UI breakdown, animation
catalog, and edge cases, read [TRIVIA.md](TRIVIA.md).

## 008 — trivia entries (opt-in enrollment)

Adds `trivia_entries` so participants explicitly opt into a trivia
before they can play, mirroring how `raffle_entries` gates raffle
eligibility:

```sql
create table public.trivia_entries (
    id              uuid primary key default gen_random_uuid(),
    quiz_id         uuid not null references trivia_quizzes(id) on delete cascade,
    participant_id  uuid not null references meetup_participants(id) on delete cascade,
    client_id       text,                              -- denormalized for app_users joins
    created_at      timestamptz not null default now(),
    unique(quiz_id, participant_id)
);
```

UX consequence on the client:

- LOBBY card surfaces an **Enter trivia** button for each participant,
  and an **Enroll everyone** button for the host (bulk-inserts every
  meetup_participants row).
- When the round starts, only enrolled clients can tap the four colored
  choice buttons. Non-enrolled devices stay in spectator mode: the
  countdown / prompt / answer-reveal animations are visible but the
  buttons are disabled with a "you're spectating" hint.

The `client_id` column is denormalized from
`meetup_participants.client_id` so the spectator gate in
`QuestionScreen` can answer "is this device enrolled?" with a single
`entries.any { it.clientId == myClientId }` lookup, and so a future
`trivia_eligibility` view that joins straight to `app_users` doesn't
need an extra hop. The `(quiz_id, participant_id)` UNIQUE makes the
upsert from "Enter" or "Enroll all" idempotent — a second tap is a
silent no-op, never a 409.

## 009 — trivia question types (boolean / multiple / numeric)

Extends the trivia game beyond single-choice. Adds:

```sql
create type trivia_question_kind as enum ('single', 'boolean', 'multiple', 'numeric');

alter table trivia_questions
    add column question_type     trivia_question_kind not null default 'single',
    add column expected_number   numeric,
    add column numeric_tolerance numeric not null default 0;

alter table trivia_answers
    add column choice_ids    uuid[],
    add column numeric_value numeric,
    alter column choice_id drop not null;
```

The same `BEFORE INSERT` trigger (`trivia_compute_answer_score`) gets
three new correctness branches:

- `single` / `boolean` → existing `trivia_choices.is_correct` lookup.
- `multiple` → set-equality between submitted `choice_ids` and the
  set of choices with `is_correct=true`. All-or-nothing; the trigger
  sorts both arrays so client order doesn't matter.
- `numeric` → `abs(numeric_value - expected_number) <= numeric_tolerance`.

The Kahoot scoring formula is unchanged (500 floor + 500 speed bonus);
only the correctness check fans out per type. Boolean rides the same
plumbing as single-choice with two pre-baked rows ("True" / "False"
at positions 0 / 1) so the trigger doesn't need to know the type
exists.

UX consequence on the client:

- "Add question" opens a four-card type chooser (Single / True-False /
  Multiple / Numeric), then a per-type editor variant.
- Gameplay renders four distinct UIs: Kahoot tiles, big two-tile T/F,
  toggleable tiles + Submit, and decimal input + Submit.
- The pure-Kotlin `TriviaScoring` mirror grows two helpers
  (`isMultipleCorrect`, `isNumericCorrect`) that match the trigger so
  `TriviaScoringTest` can lock the parity contract.

For the full per-screen breakdown read [TRIVIA.md →
Question types](TRIVIA.md#question-types).

## Adding a new migration

1. Pick the next sequence number. The format is `<NNN>_<short_kebab>.sql`.
2. Open the file with a header comment block explaining **what** and
   **why** — the schema reads as a story over time, not just a SQL log.
3. Use `if not exists` / `on conflict do nothing` / `do $$ ... exception when ... null; end$$;`
   wrappers so the file is idempotent.
4. If the migration introduces a new table that powers a live UI:
   - Enable RLS and add the four `_anon_*` policies.
   - Add it to the `supabase_realtime` publication.
   - Document the realtime feed in [APP_OVERVIEW](APP_OVERVIEW.md).
5. If the migration is a one-shot data fix, add a comment that
   explicitly says so and explain when re-running it would be a no-op.
6. Run `./scripts/supabase_apply.sh` and check the output line by line.
7. Stage the file plus the doc updates (`APP_OVERVIEW.md`,
   `IDENTITY_AND_AVATARS.md` if relevant, this page) in the same
   commit.

## Common mistakes to avoid

1. **Editing an existing migration after it has been applied to any
   database.** Always add a new file. The script applies in lexical
   order; mutating a past migration breaks reproducibility.
2. **Forgetting to add a new table to the realtime publication.** The
   `do $$ … alter publication supabase_realtime add table … $$;`
   block in `001_init.sql` is the canonical pattern; copy it.
3. **Using `truncate` instead of `delete`.** `TRUNCATE` requires
   superuser-level perms on Supabase and bypasses triggers; `DELETE …
   CASCADE` is what we use.
4. **Forgetting `grant select, insert, update, delete on … to anon, authenticated`**
   when adding a table outside the Supabase Studio UI. Direct-SQL
   tables don't auto-grant these, and the API will return 42501
   permission errors.
