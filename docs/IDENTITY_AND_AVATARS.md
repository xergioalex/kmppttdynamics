# Identity & avatars

> The single most important page to read before touching anything that
> involves a user's profile, the avatar picker, or the participant list.

This document explains how the app answers "**who is this person**" across
every device, every meetup, and every realtime surface. It covers the
two layers of identity (server `app_users` row + per-meetup
`meetup_participants` row), how the avatar picker enforces uniqueness in
real time, and the gotchas you must respect when adding new features
that involve a user's name or avatar.

## Two layers of identity

| Layer | Where it lives | Scope | Stable across? |
|---|---|---|---|
| **Install client id** | `AppSettings.installClientId()` (multiplatform-settings) | This install on this device | App restarts ✓ · App reinstalls ✗ · Hot reloads ✓ |
| **App user** | `public.app_users` (Supabase) keyed by `client_id` | Cross-meetup profile (display name + avatar) | App restarts ✓ · Re-launches on multiple devices ✗ (each device = own row) |
| **Meetup participant** | `public.meetup_participants` keyed by `(meetup_id, client_id)` | Per-meetup attendance (role, online flag) | A device that re-joins a meetup hits the SAME row, never a duplicate |

The install client id is the spine that ties everything together:

```
device                 ┌──────────────────────────┐
─────                  │ AppSettings              │
SharedPreferences      │  installClientId() = X   │
NSUserDefaults         └──────────────┬───────────┘
java.util.prefs                       │
localStorage                          │
                                      │ used as primary key
                                      ▼
                       ┌──────────────────────────┐
                       │ app_users                │
                       │  client_id = X (PK)      │   ← cross-meetup profile
                       │  display_name            │
                       │  avatar_id (UNIQUE)      │
                       └──────────────┬───────────┘
                                      │ joined to
                                      ▼
                       ┌──────────────────────────┐
                       │ meetup_participants      │
                       │  meetup_id, client_id    │   ← one per (meetup, device)
                       │  display_name, role,     │      enforced by partial
                       │  is_online, ...          │      unique index
                       └──────────────────────────┘
```

`client_id` lives in `app_users.client_id` (PK) and `meetup_participants.client_id`. Whenever you need to render an avatar or a name, you start from a participant row, look up its `client_id`, and resolve it against the `app_users` map cached in the room view model.

## Onboarding flow

1. App boots. `App.kt` reads `container.settings.profile`. If `null`, the rest of the app is unreachable until onboarding completes.
2. `OnboardingScreen(editing = false)` mounts. `OnboardingViewModel`:
   - Subscribes to `users.observeAll()` (every device's `app_users` row).
   - Computes `takenAvatars = allUsers.filter { it.clientId != myClientId }.map { it.avatarId }.toSet()`.
   - Picks a random avatar **not in `taken`** as the initial selection so first-time users don't all default to avatar #1.
3. User taps "Pick another avatar" → the same screen swaps into a `LazyVerticalGrid` of all 132 avatars. Taken avatars render at 35% opacity with a 🔒 overlay and a "Used by <name>" label. Unselectable.
4. User picks a name (≥ 2 chars), taps **Continue**. View model:
   - Calls `users.upsert(AppUserDraft(clientId = X, displayName, avatarId))`. Postgres unique index on `avatar_id` rejects races between two devices fighting over the same avatar — the loser sees a constraint violation surfaced as the screen's `errorMessage`.
   - On success, `settings.setProfile(displayName, avatarId)` writes the profile to `multiplatform-settings` and emits a non-null value into the `profile: StateFlow<LocalProfile?>`.
5. `App.kt` re-composes; the `profile == null` gate flips and the user lands on Home.

The same screen is reused for editing (`editing = true`) — initial selection is the user's current avatar, and the "Save" button replaces "Continue". Editing path goes through `users.upsert(...)`; same realtime event flows out so every other device's picker re-locks the new avatar instantly.

## Avatar storage and the `AvatarImage` composable

- 132 avatars are bundled in `composeApp/src/commonMain/composeResources/files/avatars/<id>.png` (1-indexed).
- Source files came from `assets/avatars/all/`. We ran `sips -Z 192` and `pngquant --quality=70-90` over them, dropping bundle size from 21 MB to 3.5 MB. If you replace the avatars, mirror that pipeline (see [Performance](PERFORMANCE.md#avatar-bundle)).
- `ui/components/AvatarImage.kt` exposes:
  - `AvatarImage(avatarId: Int, size: Dp)` — bytes loaded once via `Res.readBytes("files/avatars/$id.png")`, decoded with `decodeToImageBitmap()`, the resulting `ImageBitmap` is `remember`ed so re-composition inside lazy lists doesn't re-decode.
  - `TOTAL_AVATARS = 132` — single source of truth for the picker grid.
- `ui/room/tabs/ChatTab.AvatarOrPlaceholder` is the row-level helper used by every other tab (chat, Q&A, hand, raffles). It draws the avatar when `avatarId != null` and falls back to a circular `surfaceVariant` placeholder otherwise (legacy rows from before the migration that still have `client_id = NULL`).

## Realtime resolution: `usersByClientId`

`RoomViewModel` maintains two maps that every tab reads:

```kotlin
data class RoomUiState(
    val participants: List<MeetupParticipant>,            // realtime feed
    val usersByClientId: Map<String, AppUser>,            // realtime feed
    ...
)
```

- `participants` comes from `participants.observe(meetupId)`.
- `usersByClientId` comes from `users.observeAll()` (no `meetup_id` filter — `app_users` is global).

To resolve "what avatar should this row show", every tab does:

```kotlin
val participant = participantsById[someParticipantId]
val avatarId = participant?.clientId?.let { usersByClientId[it]?.avatarId }
val displayName = usersByClientId[participant?.clientId]?.displayName ?: participant?.displayName
```

`MeetupParticipant.displayName` is captured at join time and updated when the user re-joins. `app_users.display_name` is the latest profile name. We prefer the participant copy because it doesn't change mid-meetup (so renaming yourself doesn't rewrite chat history).

## The "no two users share an avatar" guarantee

There are three layers reinforcing this:

1. **UI**: the picker disables locked avatars and refuses `pickAvatar(id)` when `id in takenAvatars`.
2. **Pre-check**: `OnboardingViewModel.canSubmit` returns false when `selectedAvatar in takenAvatars`.
3. **Database**: `public.app_users` has `avatar_id int not null unique`. Two devices that race to pick #42 will see the loser's `upsert` fail with a 23505 constraint violation, which `OnboardingViewModel.submit()` catches into `errorMessage` so the user knows to pick again.

Layers 1 + 2 are reactive — they rely on the realtime `users.observeAll()` feed staying healthy. Layer 3 is the safety net.

## Profile chip on Home

`HomeScreen.ProfileChip` reads `container.settings.profile` (a `StateFlow<LocalProfile?>`) and renders the user's avatar + name + "Tap to change profile". Tapping navigates to `Screen.EditProfile`, which mounts `OnboardingScreen(editing = true)`.

Renaming or re-picking an avatar from there:

- Updates `app_users` via `users.upsert(...)`.
- Updates `AppSettings.profile` so the chip refreshes.
- Does **not** retroactively rewrite `meetup_participants.display_name` for past meetups.
  Re-joining (or `participants.join` running again as part of room re-entry) overwrites the participant row's `display_name` with the latest profile name.

## What "join a meetup" looks like now

There is no longer a `JoinMeetupScreen`. The home view model's `onEnterMeetup(meetupId)` resolves the seat without prompting:

1. **Server lookup** by `(meetup_id, client_id)` → if found, reuse and flip `is_online = true`.
2. **Cache fallback** (`AppSettings.participantIdFor(meetupId)`):
   - Pre-migration row whose `client_id IS NULL` → `participants.claim(participantId, clientId)` locks it onto this device.
   - Row already owned by us → reuse.
   - Row owned by a different `client_id` → ignore (treat as "no row").
3. **Auto-join** with `participants.join(meetupId, profile.displayName, role = PARTICIPANT, clientId)`.

The `(meetup_id, client_id)` partial unique index is the hard guarantee: a device that re-enters always lands on its existing row, never a duplicate, even if the local cache is empty (fresh install) or stale (other device cleared their data).

`CreateMeetupViewModel` calls `participants.join(... role = HOST, clientId)` once the meetup is created. The same find-then-update path means the creator can leave and come back without losing host status.

## Where avatars show up

| Surface | Avatar size | Lookup helper |
|---|---|---|
| `OnboardingScreen` preview | 144 dp circle | direct |
| `AvatarPickerView` grid tile | 76 dp + 🔒 overlay | direct |
| `HomeScreen.ProfileChip` | 40 dp | direct (from `AppSettings.profile`) |
| `RoomScreen` Members tab | 44 dp + presence dot overlay | `usersByClientId[participant.clientId]?.avatarId` |
| `ChatTab` message rows | 36 dp (32 dp on announcements) | same |
| `QATab` question rows | 28 dp (asker info row) | same |
| `HandTab` hand queue rows | 36 dp | same |
| `PollsTab` poll cards | 28 dp (creator info row) | `poll.createdBy` → participant → user |
| `RafflesTab` `EntryAvatarStack` | 28 dp, overlapping `-12 dp` | same |
| `RafflesTab` `WinnerReveal` | 84 dp circle | same |

`AvatarOrPlaceholder` (in `tabs/ChatTab.kt`, `internal`) is the helper every tab reuses — it falls back to a `surfaceVariant` circle when `avatarId == null` so legacy / pre-migration rows keep the same row shape.

## Common mistakes to avoid

1. **Reading `MeetupParticipant.displayName` for the live profile name in headers / chips**. Use the `app_users` lookup if you want the up-to-date name. Use the participant copy if you want the name as it was when the user joined.
2. **Filtering taken avatars without excluding your own clientId**. The picker would lock your current avatar to yourself otherwise.
3. **Picking a random initial avatar without checking against `taken`**. First-time users with crowded rooms could land on a taken avatar; submit would fail.
4. **Trusting `client_id IS NULL` rows to belong to anyone**. They are pre-migration ghosts. Use `findByClientId` first; only fall back to claiming a NULL row when the local `participantIdFor(meetupId)` cache points to it.
5. **Hardcoding `composeResources/files/avatars/<n>.png` paths somewhere other than `AvatarImage`**. Centralise the lookup so adding webp / dark-theme variants in the future is one change.

## Where to look in the code

| Concern | File |
|---|---|
| Onboarding screen + picker | `ui/onboarding/OnboardingScreen.kt` + `OnboardingViewModel.kt` |
| Avatar rendering | `ui/components/AvatarImage.kt` |
| Avatar fallback in room tabs | `ui/room/tabs/ChatTab.kt` (`AvatarOrPlaceholder`) |
| Profile persistence | `settings/AppSettings.kt` (`profile` flow + `installClientId()`) |
| Server-side identity | `appusers/AppUserRepository.kt`, `domain/AppUser.kt` |
| Participant identity | `participants/ParticipantRepository.kt`, `domain/MeetupParticipant.kt` |
| Database schema | `supabase/migrations/006_app_users.sql`, `supabase/migrations/004_add_client_id.sql` |
| Routing gate | `App.kt` (the `if (profile == null)` block) |
