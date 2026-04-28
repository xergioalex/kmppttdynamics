-- 004_add_client_id.sql
-- =============================================================
-- Add a stable per-install identifier to participant rows and enforce
-- uniqueness at the database level so the same device joining the same
-- meetup twice always lands on the same row, no matter what the client
-- cache says.
--
-- Why this exists: until now, a participant's identity was tracked
-- purely by the auto-generated UUID `id`, with the device caching that
-- id locally in `AppSettings`. If the cache got invalidated (network
-- blip, manual SQL cleanup, fresh install, MainActivity recreation,
-- hot reload), the device would re-route through the Join screen and
-- INSERT a brand-new row with a brand-new id — leaving the previous
-- row behind as a duplicate "ghost". With two emulators that hot-
-- reload often, rooms quickly piled up four or five copies of every
-- participant.
--
-- By giving every device a stable `client_id` (persisted via
-- `AppSettings.installClientId()`), and adding a `(meetup_id, client_id)`
-- unique partial index, the Postgres layer becomes the source of truth:
-- the device's join code path can safely upsert and we are guaranteed
-- one row per (meetup, install).
--
-- The index is partial (`where client_id is not null`) so existing
-- rows that pre-date this migration — and therefore have no client_id
-- — don't conflict. Those rows can no longer be reactivated by any
-- device (no install_client_id matches NULL), so they read as offline
-- ghosts. The host can remove them via the Members tab.

alter table public.meetup_participants
    add column if not exists client_id text;

create unique index if not exists meetup_participants_meetup_client_unique
    on public.meetup_participants (meetup_id, client_id)
    where client_id is not null;
