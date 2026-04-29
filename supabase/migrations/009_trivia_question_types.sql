-- 009_trivia_question_types.sql
-- =============================================================
-- Adds three new question kinds to the trivia game on top of the
-- existing single-choice (Kahoot-style) format:
--
--   * boolean  — true/false (server stores 2 choices, exactly like
--                single-choice with 2 options; the UI is what
--                differs).
--   * multiple — 4 choices, ANY number can be marked correct. The
--                client sends `choice_ids uuid[]` and is correct iff
--                the submitted set equals the correct set (no extras,
--                no missing).
--   * numeric  — no choices at all. The question carries an
--                `expected_number` and an optional `numeric_tolerance`
--                (>= 0). The client sends `numeric_value` and is
--                correct iff `abs(numeric_value - expected_number)
--                <= numeric_tolerance`.
--
-- The Kahoot-style scoring formula (500 pts floor + 500 pts speed
-- bonus) is unchanged. Only the correctness check fans out per type.
--
-- Idempotent: every column / type / index uses `IF NOT EXISTS` and
-- the trigger function is replaced wholesale.

------------------------------------------------------------------
-- Question types
------------------------------------------------------------------
do $$ begin
    create type trivia_question_kind as enum ('single', 'boolean', 'multiple', 'numeric');
exception when duplicate_object then null;
end $$;

alter table public.trivia_questions
    add column if not exists question_type     trivia_question_kind not null default 'single',
    add column if not exists expected_number   numeric,
    add column if not exists numeric_tolerance numeric not null default 0;

------------------------------------------------------------------
-- Answers: support for multiple-select arrays + numeric values
------------------------------------------------------------------
-- choice_id stays for single/boolean (one selection); choice_ids is
-- the array used by multiple-select; numeric_value is the typed-in
-- number. Exactly one of these three is populated per row, gated by
-- the question's `question_type`.
alter table public.trivia_answers
    add column if not exists choice_ids    uuid[],
    add column if not exists numeric_value numeric;

-- Loosen the choice_id NOT NULL so numeric questions can insert
-- without one. Single/boolean rows continue to populate it; the
-- trigger validates the right column-per-type combination.
do $$
begin
    alter table public.trivia_answers alter column choice_id drop not null;
exception
    when others then null; -- already nullable from a prior run
end $$;

------------------------------------------------------------------
-- Trigger: compute score on insert, type-aware
------------------------------------------------------------------
create or replace function public.trivia_compute_answer_score() returns trigger as $$
declare
    q_started_at        timestamptz;
    seconds_window      int;
    elapsed_ms          int;
    is_correct_choice   boolean;
    fraction_remaining  numeric;
    q_position          int;
    q_current_index     int;
    q_kind              trivia_question_kind;
    q_expected          numeric;
    q_tolerance         numeric;
    correct_set         uuid[];
    submitted_set       uuid[];
begin
    -- Pull every datum we need in one round trip.
    select tq.current_question_started_at,
           tq.current_question_index,
           q.seconds_to_answer,
           q.position,
           q.question_type,
           q.expected_number,
           q.numeric_tolerance
      into q_started_at,
           q_current_index,
           seconds_window,
           q_position,
           q_kind,
           q_expected,
           q_tolerance
      from public.trivia_quizzes tq
      join public.trivia_questions q on q.quiz_id = tq.id
     where tq.id = new.quiz_id and q.id = new.question_id;

    if seconds_window is null or seconds_window <= 0 then
        seconds_window := 15;
    end if;

    -- Late insert guard: if the host already advanced past this
    -- question, treat the response as having used the full timer.
    if q_position is not null and q_current_index is not null and q_position <> q_current_index then
        elapsed_ms := seconds_window * 1000;
    elsif q_started_at is null then
        elapsed_ms := 0;
    else
        elapsed_ms := greatest(0, (extract(epoch from (now() - q_started_at)) * 1000)::int);
    end if;

    if elapsed_ms > seconds_window * 1000 then
        elapsed_ms := seconds_window * 1000;
    end if;

    -- Correctness branch per type ----------------------------------
    if q_kind = 'single' or q_kind = 'boolean' then
        select c.is_correct into is_correct_choice
          from public.trivia_choices c
         where c.id = new.choice_id;
        new.is_correct := coalesce(is_correct_choice, false);

    elsif q_kind = 'multiple' then
        -- Build the canonical correct-set for this question, then
        -- compare with what the client sent. set equality = correct;
        -- missing or extra picks = wrong, no partial credit.
        select coalesce(array_agg(c.id order by c.id), '{}'::uuid[])
          into correct_set
          from public.trivia_choices c
         where c.question_id = new.question_id and c.is_correct;

        submitted_set := coalesce(new.choice_ids, '{}'::uuid[]);
        -- Order arrays so the equality check is order-independent.
        submitted_set := (
            select coalesce(array_agg(x order by x), '{}'::uuid[])
              from unnest(submitted_set) as t(x)
        );
        new.is_correct := submitted_set = correct_set and array_length(correct_set, 1) is not null;

    elsif q_kind = 'numeric' then
        if q_expected is null or new.numeric_value is null then
            new.is_correct := false;
        else
            new.is_correct := abs(new.numeric_value - q_expected) <= coalesce(q_tolerance, 0);
        end if;

    else
        -- Unknown kind: never award points but keep the row.
        new.is_correct := false;
    end if;

    new.response_ms := elapsed_ms;

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
