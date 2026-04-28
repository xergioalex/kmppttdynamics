-- Pereira Tech Talks Dynamics — initial schema
-- =============================================================
-- Each meetup is a realtime room. Every dynamic (chat, hand raise,
-- Q&A, polls, raffles, activity) is scoped by meetup_id so a room
-- subscription cleanly partitions all live data.
--
-- Run with:    ./scripts/supabase_apply.sh
-- or manually: psql "$SUPABASE_DB_URL" -f supabase/migrations/001_init.sql
--
-- This file is idempotent (CREATE ... IF NOT EXISTS) so it can be
-- applied repeatedly to the same database during development.

create extension if not exists "pgcrypto";

------------------------------------------------------------------
-- profiles (optional, for future Supabase Auth users)
------------------------------------------------------------------
create table if not exists public.profiles (
    id           uuid primary key,
    display_name text not null,
    avatar_url   text,
    created_at   timestamptz not null default now()
);

------------------------------------------------------------------
-- meetups
------------------------------------------------------------------
create table if not exists public.meetups (
    id          uuid primary key default gen_random_uuid(),
    title       text not null,
    description text,
    join_code   text not null unique,
    -- draft | live | paused | ended | archived
    status      text not null default 'draft',
    created_by  uuid,
    starts_at   timestamptz,
    ended_at    timestamptz,
    created_at  timestamptz not null default now()
);

create index if not exists meetups_status_idx     on public.meetups(status);
create index if not exists meetups_join_code_idx  on public.meetups(join_code);

------------------------------------------------------------------
-- meetup_participants
------------------------------------------------------------------
create table if not exists public.meetup_participants (
    id            uuid primary key default gen_random_uuid(),
    meetup_id     uuid not null references public.meetups(id) on delete cascade,
    user_id       uuid,
    display_name  text not null,
    -- host | participant | moderator
    role          text not null default 'participant',
    is_online     boolean not null default false,
    joined_at     timestamptz not null default now(),
    last_seen_at  timestamptz
);

create index if not exists participants_meetup_idx on public.meetup_participants(meetup_id);
create index if not exists participants_online_idx on public.meetup_participants(meetup_id, is_online);

------------------------------------------------------------------
-- chat_messages  (also serves announcements via type='announcement')
------------------------------------------------------------------
create table if not exists public.chat_messages (
    id             uuid primary key default gen_random_uuid(),
    meetup_id      uuid not null references public.meetups(id) on delete cascade,
    participant_id uuid references public.meetup_participants(id) on delete set null,
    message        text not null,
    -- message | announcement | system
    type           text not null default 'message',
    -- visible | hidden | deleted
    status         text not null default 'visible',
    created_at     timestamptz not null default now()
);

create index if not exists chat_meetup_created_idx on public.chat_messages(meetup_id, created_at);

------------------------------------------------------------------
-- raised_hands
------------------------------------------------------------------
create table if not exists public.raised_hands (
    id               uuid primary key default gen_random_uuid(),
    meetup_id        uuid not null references public.meetups(id) on delete cascade,
    participant_id   uuid not null references public.meetup_participants(id) on delete cascade,
    message          text,
    -- raised | acknowledged | speaking | lowered | dismissed
    status           text not null default 'raised',
    raised_at        timestamptz not null default now(),
    acknowledged_at  timestamptz,
    lowered_at       timestamptz
);

create index if not exists hands_meetup_status_idx on public.raised_hands(meetup_id, status, raised_at);

-- Only one active raised hand per participant per meetup.
create unique index if not exists hands_active_unique
    on public.raised_hands(meetup_id, participant_id)
    where status in ('raised', 'acknowledged', 'speaking');

------------------------------------------------------------------
-- questions  (Q&A)
------------------------------------------------------------------
create table if not exists public.questions (
    id             uuid primary key default gen_random_uuid(),
    meetup_id      uuid not null references public.meetups(id) on delete cascade,
    participant_id uuid references public.meetup_participants(id) on delete set null,
    question       text not null,
    -- open | answered | hidden
    status         text not null default 'open',
    upvotes_count  int  not null default 0,
    created_at     timestamptz not null default now(),
    answered_at    timestamptz
);

create index if not exists questions_meetup_idx on public.questions(meetup_id, created_at);

------------------------------------------------------------------
-- question_votes
------------------------------------------------------------------
create table if not exists public.question_votes (
    id             uuid primary key default gen_random_uuid(),
    question_id    uuid not null references public.questions(id) on delete cascade,
    participant_id uuid not null references public.meetup_participants(id) on delete cascade,
    created_at     timestamptz not null default now(),
    unique(question_id, participant_id)
);

-- Trigger that keeps questions.upvotes_count in sync.
create or replace function public.questions_recount_upvotes() returns trigger as $$
begin
    if (tg_op = 'INSERT') then
        update public.questions
           set upvotes_count = upvotes_count + 1
         where id = new.question_id;
        return new;
    elsif (tg_op = 'DELETE') then
        update public.questions
           set upvotes_count = greatest(upvotes_count - 1, 0)
         where id = old.question_id;
        return old;
    end if;
    return null;
end;
$$ language plpgsql;

drop trigger if exists question_votes_recount on public.question_votes;
create trigger question_votes_recount
    after insert or delete on public.question_votes
    for each row execute function public.questions_recount_upvotes();

------------------------------------------------------------------
-- polls
------------------------------------------------------------------
create table if not exists public.polls (
    id             uuid primary key default gen_random_uuid(),
    meetup_id      uuid not null references public.meetups(id) on delete cascade,
    created_by     uuid references public.meetup_participants(id) on delete set null,
    question       text not null,
    -- draft | open | closed | archived
    status         text not null default 'draft',
    is_anonymous   boolean not null default true,
    created_at     timestamptz not null default now(),
    opened_at      timestamptz,
    closed_at      timestamptz
);

create index if not exists polls_meetup_idx on public.polls(meetup_id, created_at);

create table if not exists public.poll_options (
    id         uuid primary key default gen_random_uuid(),
    poll_id    uuid not null references public.polls(id) on delete cascade,
    text       text not null,
    position   int  not null default 0,
    created_at timestamptz not null default now()
);

create index if not exists poll_options_poll_idx on public.poll_options(poll_id, position);

create table if not exists public.poll_votes (
    id             uuid primary key default gen_random_uuid(),
    poll_id        uuid not null references public.polls(id) on delete cascade,
    option_id      uuid not null references public.poll_options(id) on delete cascade,
    participant_id uuid not null references public.meetup_participants(id) on delete cascade,
    created_at     timestamptz not null default now(),
    unique(poll_id, participant_id)
);

create index if not exists poll_votes_poll_idx on public.poll_votes(poll_id);

------------------------------------------------------------------
-- raffles
------------------------------------------------------------------
create table if not exists public.raffles (
    id         uuid primary key default gen_random_uuid(),
    meetup_id  uuid not null references public.meetups(id) on delete cascade,
    created_by uuid references public.meetup_participants(id) on delete set null,
    title      text not null,
    -- draft | open | drawn | closed | archived
    status     text not null default 'draft',
    created_at timestamptz not null default now(),
    opened_at  timestamptz,
    drawn_at   timestamptz,
    closed_at  timestamptz
);

create index if not exists raffles_meetup_idx on public.raffles(meetup_id);

create table if not exists public.raffle_entries (
    id             uuid primary key default gen_random_uuid(),
    raffle_id      uuid not null references public.raffles(id) on delete cascade,
    participant_id uuid not null references public.meetup_participants(id) on delete cascade,
    created_at     timestamptz not null default now(),
    unique(raffle_id, participant_id)
);

create table if not exists public.raffle_winners (
    id             uuid primary key default gen_random_uuid(),
    raffle_id      uuid not null references public.raffles(id) on delete cascade,
    participant_id uuid not null references public.meetup_participants(id) on delete cascade,
    drawn_at       timestamptz not null default now()
);

------------------------------------------------------------------
-- activity_events  (live feed of room highlights)
------------------------------------------------------------------
create table if not exists public.activity_events (
    id                   uuid primary key default gen_random_uuid(),
    meetup_id            uuid not null references public.meetups(id) on delete cascade,
    type                 text not null,
    actor_participant_id uuid references public.meetup_participants(id) on delete set null,
    payload              jsonb,
    created_at           timestamptz not null default now()
);

create index if not exists activity_meetup_idx on public.activity_events(meetup_id, created_at);

------------------------------------------------------------------
-- Realtime publication
------------------------------------------------------------------
-- Supabase ships a `supabase_realtime` publication. We add every table
-- that powers a live UI to it so Postgres-changes subscriptions deliver
-- inserts/updates/deletes to clients.
--
-- We loop one table at a time and swallow `duplicate_object` so a
-- second run of this migration is a no-op even if some tables were
-- already members of the publication.
do $$
declare
    t text;
    realtime_tables text[] := array[
        'meetups',
        'meetup_participants',
        'chat_messages',
        'raised_hands',
        'questions',
        'question_votes',
        'polls',
        'poll_options',
        'poll_votes',
        'raffles',
        'raffle_entries',
        'raffle_winners',
        'activity_events'
    ];
begin
    if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        return;
    end if;

    foreach t in array realtime_tables loop
        begin
            execute format('alter publication supabase_realtime add table public.%I', t);
        exception
            when duplicate_object then null;     -- already in publication
            when undefined_object then null;     -- publication missing on this DB
        end;
    end loop;
end$$;

------------------------------------------------------------------
-- Row Level Security (MVP — permissive, for demo only)
------------------------------------------------------------------
-- WARNING: For production, tighten these policies. The MVP allows
-- anyone with the anon key to read/insert into rooms because
-- participants are anonymous. Host-only actions are enforced by the
-- client today; that is NOT secure. Track this in the README.
alter table public.meetups               enable row level security;
alter table public.meetup_participants   enable row level security;
alter table public.chat_messages         enable row level security;
alter table public.raised_hands          enable row level security;
alter table public.questions             enable row level security;
alter table public.question_votes        enable row level security;
alter table public.polls                 enable row level security;
alter table public.poll_options          enable row level security;
alter table public.poll_votes            enable row level security;
alter table public.raffles               enable row level security;
alter table public.raffle_entries        enable row level security;
alter table public.raffle_winners        enable row level security;
alter table public.activity_events       enable row level security;

-- Helper to (re)create a policy without complaining if it exists.
do $$
declare
    t text;
    tables text[] := array[
        'meetups',
        'meetup_participants',
        'chat_messages',
        'raised_hands',
        'questions',
        'question_votes',
        'polls',
        'poll_options',
        'poll_votes',
        'raffles',
        'raffle_entries',
        'raffle_winners',
        'activity_events'
    ];
begin
    foreach t in array tables loop
        execute format('drop policy if exists "%s_anon_select" on public.%s', t, t);
        execute format('drop policy if exists "%s_anon_insert" on public.%s', t, t);
        execute format('drop policy if exists "%s_anon_update" on public.%s', t, t);
        execute format('drop policy if exists "%s_anon_delete" on public.%s', t, t);

        execute format('create policy "%s_anon_select" on public.%s for select using (true)', t, t);
        execute format('create policy "%s_anon_insert" on public.%s for insert with check (true)', t, t);
        execute format('create policy "%s_anon_update" on public.%s for update using (true) with check (true)', t, t);
        execute format('create policy "%s_anon_delete" on public.%s for delete using (true)', t, t);
    end loop;
end$$;
