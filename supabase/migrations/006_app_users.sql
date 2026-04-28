-- 006_app_users.sql
-- =============================================================
-- Adds the global "app user" identity that is picked once on first
-- launch and reused across every meetup the device joins.
--
-- Each install (identified by `client_id`) owns:
--   - a `display_name` chosen on the onboarding screen
--   - an `avatar_id` (1..132) referencing one of the bundled avatars
--     in composeResources/files/avatars/<id>.png
--
-- The `avatar_id unique` constraint enforces the "no two users can
-- pick the same avatar" rule at the database level. Realtime change
-- streams on this table feed the avatar-picker so taken avatars show
-- up locked the moment another device claims them.

create table if not exists public.app_users (
    client_id    text primary key,
    display_name text not null,
    avatar_id    int  not null unique,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

create index if not exists app_users_avatar_idx on public.app_users(avatar_id);

-- Add to realtime publication so the avatar picker can react instantly.
do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        begin
            execute 'alter publication supabase_realtime add table public.app_users';
        exception
            when duplicate_object then null;
            when undefined_object then null;
        end;
    end if;
end$$;

-- RLS: permissive for MVP, tighten when Supabase Auth lands.
alter table public.app_users enable row level security;

drop policy if exists "app_users_anon_select" on public.app_users;
drop policy if exists "app_users_anon_insert" on public.app_users;
drop policy if exists "app_users_anon_update" on public.app_users;
drop policy if exists "app_users_anon_delete" on public.app_users;

create policy "app_users_anon_select" on public.app_users for select using (true);
create policy "app_users_anon_insert" on public.app_users for insert with check (true);
create policy "app_users_anon_update" on public.app_users for update using (true) with check (true);
create policy "app_users_anon_delete" on public.app_users for delete using (true);
