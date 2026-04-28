-- 003_dedupe_participants.sql
-- =============================================================
-- One-shot cleanup for the duplicate-participants bug.
--
-- Until HomeViewModel.onEnterMeetup learned to distinguish "cached
-- participant row was deleted server-side" from "transient network
-- error", every blip during room re-entry routed the user back to the
-- Join screen, which inserted a brand-new participant row. The result
-- was rooms full of `XergioAleX (host)`, `XergioAleX (host)`,
-- `Katherine`, `Katherine`, `Katherine` — same device, same person,
-- N rows.
--
-- This migration keeps the OLDEST row per `(meetup_id, display_name)`
-- and deletes every other duplicate. We keep the oldest one because
-- (a) any cached `participantId` on a device most likely points to it,
-- and (b) it's the one with `role = host` for the meetup creator.
--
-- It is safe to re-run: after the first execution there are no
-- duplicates left to delete.

with ranked as (
    select id,
           meetup_id,
           display_name,
           row_number() over (
               partition by meetup_id, display_name
               order by joined_at asc, id asc
           ) as rn
      from public.meetup_participants
)
delete from public.meetup_participants p
 using ranked r
 where p.id = r.id
   and r.rn > 1;
