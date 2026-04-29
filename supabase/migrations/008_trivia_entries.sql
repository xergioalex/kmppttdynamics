-- 008_trivia_entries.sql
-- =============================================================
-- Adds opt-in enrollment for trivia, mirroring the raffles pattern.
--
-- Until this migration, every device in the meetup automatically saw
-- the question screen the moment the host pressed Start, and any tap
-- counted as an answer. The new model:
--
--   * Participants explicitly *enter* a trivia (host can also bulk-
--     enroll the room with one click) during the LOBBY phase.
--   * The lobby renders the avatar stack of the enrolled set so
--     everyone can see who's in.
--   * Once started, only enrolled clients can submit answers; the rest
--     stay in spectator mode (countdown + question prompt visible but
--     the colored choice buttons are disabled).
--
-- The schema mirrors `raffle_entries` exactly: per-quiz row with a
-- UNIQUE (quiz_id, participant_id) so re-tapping "Enter" or having the
-- host enroll someone who's already in is a silent no-op via upsert.
-- We additionally store `client_id` (denormalized from the
-- meetup_participants row) so a leaderboard query that joins to
-- app_users by client_id stays single-hop.

create table if not exists public.trivia_entries (
    id              uuid primary key default gen_random_uuid(),
    quiz_id         uuid not null references public.trivia_quizzes(id) on delete cascade,
    participant_id  uuid not null references public.meetup_participants(id) on delete cascade,
    client_id       text,
    created_at      timestamptz not null default now(),
    unique(quiz_id, participant_id)
);

create index if not exists trivia_entries_quiz_idx on public.trivia_entries(quiz_id);
create index if not exists trivia_entries_client_idx on public.trivia_entries(quiz_id, client_id);

------------------------------------------------------------------
-- Realtime publication
------------------------------------------------------------------
do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        begin
            execute 'alter publication supabase_realtime add table public.trivia_entries';
        exception
            when duplicate_object then null;
            when undefined_object then null;
        end;
    end if;
end$$;

------------------------------------------------------------------
-- Row Level Security (MVP — permissive)
------------------------------------------------------------------
alter table public.trivia_entries enable row level security;

drop policy if exists "trivia_entries_anon_select" on public.trivia_entries;
drop policy if exists "trivia_entries_anon_insert" on public.trivia_entries;
drop policy if exists "trivia_entries_anon_update" on public.trivia_entries;
drop policy if exists "trivia_entries_anon_delete" on public.trivia_entries;

create policy "trivia_entries_anon_select" on public.trivia_entries for select using (true);
create policy "trivia_entries_anon_insert" on public.trivia_entries for insert with check (true);
create policy "trivia_entries_anon_update" on public.trivia_entries for update using (true) with check (true);
create policy "trivia_entries_anon_delete" on public.trivia_entries for delete using (true);
