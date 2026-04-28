-- 002_backfill_status.sql
-- =============================================================
-- One-shot fix for the kotlinx.serialization `encodeDefaults = false`
-- bug. Until @EncodeDefault was added to PollDraft.status and
-- RaffleDraft.status, every poll and raffle inserted from the client
-- ended up with status='draft' (the column default) because the JSON
-- payload silently omitted the field. This migration upgrades any such
-- rows to 'open' so they're votable / enterable from the room UI.
--
-- The Milestone-1 UX always publishes polls and raffles immediately on
-- create — there is no save-as-draft flow yet — so converting every
-- existing 'draft' row to 'open' is safe.
--
-- This file is run by ./scripts/supabase_apply.sh after 001_init.sql.

update public.polls
   set status = 'open',
       opened_at = coalesce(opened_at, now())
 where status = 'draft';

update public.raffles
   set status = 'open',
       opened_at = coalesce(opened_at, now())
 where status = 'draft';
