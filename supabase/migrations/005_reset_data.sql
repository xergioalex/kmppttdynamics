-- 005_reset_data.sql
-- =============================================================
-- Wipe all room-scoped data so we can start fresh after the
-- duplicate-participant fixes landed (client_id column, unique
-- partial index, install-stable client ids). The legacy rows from
-- before column 004 carried `client_id = NULL`, never matched the
-- new lookup path, and showed up forever as offline ghosts inflating
-- the participant counts.
--
-- This file is idempotent — re-running it just re-deletes from
-- already-empty tables. The `meetups` cascade takes care of every
-- child table (`meetup_participants`, `chat_messages`,
-- `raised_hands`, `questions`, `question_votes`, `polls`,
-- `poll_options`, `poll_votes`, `raffles`, `raffle_entries`,
-- `raffle_winners`, `activity_events`).

delete from public.meetups;
