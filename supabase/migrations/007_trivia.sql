-- 007_trivia.sql
-- =============================================================
-- Kahoot-style trivia game:
--   * Host configures a quiz with N multiple-choice questions, each
--     with exactly four choices and exactly one correct answer.
--   * Host opens a lobby; participants join and wait.
--   * Host starts the quiz. The server stamps `current_question_index`
--     and `current_question_started_at` — these two fields are the
--     single source of truth for every client's countdown timer.
--   * Each participant submits at most one `trivia_answers` row per
--     question (UNIQUE `(question_id, client_id)`). A BEFORE INSERT
--     trigger computes `is_correct`, `response_ms`, and `points_awarded`
--     server-side so the client can never inflate scores.
--   * After the last question the quiz transitions to `calculating`,
--     waits ~10 s for suspense, and then resolves to `finished` —
--     at which point the `trivia_leaderboard` view is queried for the
--     final ranking (sorted client-side: total_points DESC, then
--     avg_response_ms ASC for tie-breaking).
--
-- Idempotent (`CREATE … IF NOT EXISTS` everywhere).

------------------------------------------------------------------
-- trivia_quizzes
------------------------------------------------------------------
create table if not exists public.trivia_quizzes (
    id                              uuid primary key default gen_random_uuid(),
    meetup_id                       uuid not null references public.meetups(id) on delete cascade,
    title                           text not null,
    -- draft | lobby | in_progress | calculating | finished
    status                          text not null default 'draft',
    default_seconds_per_question    int  not null default 15,
    current_question_index          int,
    current_question_started_at     timestamptz,
    calculating_started_at          timestamptz,
    -- Stable install id of the host that created the quiz. Stored for
    -- audit / "Created by …" UI; control is gated by `role=host` on
    -- meetup_participants, not by this field.
    created_by_client_id            text,
    created_at                      timestamptz not null default now(),
    started_at                      timestamptz,
    finished_at                     timestamptz
);

create index if not exists trivia_quizzes_meetup_idx on public.trivia_quizzes(meetup_id, created_at);

------------------------------------------------------------------
-- trivia_questions
------------------------------------------------------------------
create table if not exists public.trivia_questions (
    id                  uuid primary key default gen_random_uuid(),
    quiz_id             uuid not null references public.trivia_quizzes(id) on delete cascade,
    -- 0-based ordering. Host-controlled; UNIQUE so we can advance via
    -- `current_question_index` without ambiguity.
    position            int  not null,
    prompt              text not null,
    -- Override of the quiz default. Defaults to 15 for new questions.
    seconds_to_answer   int  not null default 15,
    created_at          timestamptz not null default now(),
    unique(quiz_id, position)
);

create index if not exists trivia_questions_quiz_idx on public.trivia_questions(quiz_id, position);

------------------------------------------------------------------
-- trivia_choices
------------------------------------------------------------------
-- Locked to 4 choices per question (Kahoot-style); the app inserts
-- 4 rows on question creation. Exactly one must have `is_correct=true`,
-- enforced by the `trivia_questions_one_correct` trigger below.
create table if not exists public.trivia_choices (
    id           uuid primary key default gen_random_uuid(),
    question_id  uuid not null references public.trivia_questions(id) on delete cascade,
    position     int  not null,
    label        text not null,
    is_correct   boolean not null default false,
    created_at   timestamptz not null default now(),
    unique(question_id, position)
);

create index if not exists trivia_choices_question_idx on public.trivia_choices(question_id, position);

------------------------------------------------------------------
-- trivia_answers
------------------------------------------------------------------
-- One row per (question, device). The trigger below populates
-- `is_correct`, `response_ms`, and `points_awarded` on insert — the
-- client only sends the chosen `choice_id`.
create table if not exists public.trivia_answers (
    id              uuid primary key default gen_random_uuid(),
    quiz_id         uuid not null references public.trivia_quizzes(id) on delete cascade,
    question_id     uuid not null references public.trivia_questions(id) on delete cascade,
    participant_id  uuid not null references public.meetup_participants(id) on delete cascade,
    -- Stable install id; the leaderboard joins to app_users.client_id
    -- so the displayed avatar/name survives meetup_participants churn.
    client_id       text not null,
    choice_id       uuid not null references public.trivia_choices(id) on delete cascade,
    is_correct      boolean not null default false,
    response_ms     int    not null default 0,
    points_awarded  int    not null default 0,
    answered_at     timestamptz not null default now(),
    unique(question_id, client_id)
);

create index if not exists trivia_answers_quiz_idx     on public.trivia_answers(quiz_id);
create index if not exists trivia_answers_question_idx on public.trivia_answers(question_id);

------------------------------------------------------------------
-- Trigger: compute score on insert
------------------------------------------------------------------
-- Reads the question's `seconds_to_answer` and the quiz's
-- `current_question_started_at`, computes the elapsed milliseconds,
-- looks up `is_correct` from the chosen `trivia_choices` row, and
-- writes the Kahoot-style score (500..1000 if correct, 0 otherwise).
create or replace function public.trivia_compute_answer_score() returns trigger as $$
declare
    q_started_at        timestamptz;
    seconds_window      int;
    elapsed_ms          int;
    is_correct_choice   boolean;
    fraction_remaining  numeric;
    q_position          int;
    q_current_index     int;
begin
    -- Pull every datum we need in one round trip: when the active
    -- question started, how long it lasts, and crucially, whether the
    -- answer is still being submitted for the *current* question.
    select tq.current_question_started_at,
           tq.current_question_index,
           q.seconds_to_answer,
           q.position
      into q_started_at, q_current_index, seconds_window, q_position
      from public.trivia_quizzes tq
      join public.trivia_questions q on q.quiz_id = tq.id
     where tq.id = new.quiz_id and q.id = new.question_id;

    select c.is_correct into is_correct_choice
      from public.trivia_choices c
     where c.id = new.choice_id;

    if seconds_window is null or seconds_window <= 0 then
        seconds_window := 15;
    end if;

    -- Late insert guard: if the host already advanced past this
    -- question, treat the response as having used the full timer.
    -- Without this, the trigger would compare `now()` against the
    -- *new* question's `current_question_started_at`, which is ~0 ms
    -- old, and award full 1 000-point bonuses for a tap that landed
    -- in the network race after the timer hit zero. We keep the row
    -- (audit trail; UI's "Locked in" already showed) but score
    -- conservatively.
    if q_position is not null and q_current_index is not null and q_position <> q_current_index then
        elapsed_ms := seconds_window * 1000;
    elsif q_started_at is null then
        elapsed_ms := 0;
    else
        elapsed_ms := greatest(0, (extract(epoch from (now() - q_started_at)) * 1000)::int);
    end if;

    -- Clamp to the question window so any clock skew or network
    -- blip can never produce a negative score component.
    if elapsed_ms > seconds_window * 1000 then
        elapsed_ms := seconds_window * 1000;
    end if;

    new.response_ms := elapsed_ms;
    new.is_correct  := coalesce(is_correct_choice, false);

    if new.is_correct then
        fraction_remaining := 1.0 - (elapsed_ms::numeric / (seconds_window * 1000)::numeric);
        new.points_awarded := round(500 + 500 * fraction_remaining)::int;
    else
        new.points_awarded := 0;
    end if;

    new.answered_at := coalesce(new.answered_at, now());
    return new;
end;
$$ language plpgsql;

drop trigger if exists trivia_answers_compute_score on public.trivia_answers;
create trigger trivia_answers_compute_score
    before insert on public.trivia_answers
    for each row execute function public.trivia_compute_answer_score();

------------------------------------------------------------------
-- View: trivia_leaderboard
------------------------------------------------------------------
-- Aggregates answers per (quiz, client) and joins to app_users so
-- avatar / display name come back in a single query. The client sorts
-- the result by total_points DESC, then avg_response_ms ASC for
-- tie-breaking, then display_name ASC for full determinism.
create or replace view public.trivia_leaderboard as
select
    ta.quiz_id,
    ta.client_id,
    coalesce(au.display_name, 'Anon')                                          as display_name,
    au.avatar_id,
    coalesce(sum(ta.points_awarded), 0)::int                                   as total_points,
    count(*) filter (where ta.is_correct)::int                                  as correct_count,
    count(*)::int                                                              as answered_count,
    coalesce(avg(ta.response_ms) filter (where ta.is_correct), 0)::int          as avg_response_ms
  from public.trivia_answers ta
  left join public.app_users au on au.client_id = ta.client_id
  group by ta.quiz_id, ta.client_id, au.display_name, au.avatar_id;

------------------------------------------------------------------
-- Realtime publication membership
------------------------------------------------------------------
-- Add the four base tables. The view is queried on demand (no need
-- to subscribe to it directly; the `trivia_answers` channel fires
-- whenever the leaderboard could change).
do $$
declare
    t text;
    realtime_tables text[] := array[
        'trivia_quizzes',
        'trivia_questions',
        'trivia_choices',
        'trivia_answers'
    ];
begin
    if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        return;
    end if;

    foreach t in array realtime_tables loop
        begin
            execute format('alter publication supabase_realtime add table public.%I', t);
        exception
            when duplicate_object then null;
            when undefined_object then null;
        end;
    end loop;
end$$;

------------------------------------------------------------------
-- Row Level Security (MVP — permissive, mirrors 001_init.sql)
------------------------------------------------------------------
alter table public.trivia_quizzes   enable row level security;
alter table public.trivia_questions enable row level security;
alter table public.trivia_choices   enable row level security;
alter table public.trivia_answers   enable row level security;

do $$
declare
    t text;
    tables text[] := array[
        'trivia_quizzes',
        'trivia_questions',
        'trivia_choices',
        'trivia_answers'
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

-- Grant the leaderboard view to anon/authenticated so the client can
-- read it. Tables already inherit grants via the schema-level grant in
-- 001_init.sql, but views need to be granted explicitly because they
-- are created after that bulk grant runs.
grant select on public.trivia_leaderboard to anon, authenticated;
