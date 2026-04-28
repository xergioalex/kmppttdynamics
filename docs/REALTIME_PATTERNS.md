# Realtime patterns

Every live UI in the app is powered by a Supabase Realtime channel
plus a REST refresh on every change. The pattern is uniform across
seven repositories — `meetups`, `participants`, `chat`, `hands`,
`questions`, `polls`, `raffles`, `app_users` — and follows the same
shape so a new feature can copy the template verbatim.

This page documents the contract, the gotchas we hit during M1–M5,
and the helper utility every observer must use.

## The standard observer template

```kotlin
fun observe(meetupId: String): Flow<List<X>> = flow {
    val channel = supabase.channel(uniqueRealtimeTopic("x_$meetupId"))
    val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = TABLE
        filter("meetup_id", FilterOperator.EQ, meetupId)
    }
    channel.subscribe()
    try {
        emit(list(meetupId))                              // initial REST snapshot
        kotlinx.coroutines.delay(750)                     // catch-up window
        emit(list(meetupId))                              // catch-up fetch
        changes.collect { emit(list(meetupId)) }          // refresh on every event
    } finally {
        withContext(NonCancellable) { channel.unsubscribe() }
    }
}
```

Every part of that template is load-bearing. Don't trim it.

## 1 · Why the channel name must be unique per call

`supabase-kt` keeps a `subscriptions: Map<String, RealtimeChannel>`
inside `RealtimeImpl`. When two callers ask for `supabase.channel("X")`
they get back **the same `RealtimeChannel` instance**.

When the first caller's flow ends and runs `finally { channel.unsubscribe() }`,
the underlying channel transitions to `UNSUBSCRIBED` for **every** other
caller still listening. The second caller's flow stays alive but no
events ever arrive — the picker silently freezes, the chat stops
updating, the participant list goes stale.

We hit this every time a user opened the avatar picker more than once
during a session, because both `OnboardingViewModel` and `RoomViewModel`
called `users.observeAll()` against the shared `app_users` topic.

The fix is the `uniqueRealtimeTopic(base)` helper:

```kotlin
// supabase/RealtimeChannelNames.kt
internal fun uniqueRealtimeTopic(base: String): String {
    val suffix = (1..8).map { Random.nextInt(0, 16).toString(16) }.joinToString("")
    return "${base}_$suffix"
}
```

Every observer in this codebase uses it. **New observers MUST use it
too** — never call `supabase.channel("static_topic")` directly.

> **Side effect**: each device opens N channels for N concurrent
> observers, where before two consumers shared one. It's still well
> within Supabase's free-tier 200 concurrent subscriptions, and it's
> the only correct behaviour.

## 2 · Why the catch-up `delay(750)` exists

`channel.subscribe()` is a fire-and-forget call. It returns
immediately, but the websocket JOIN handshake completes asynchronously.
There is a window — usually a few hundred milliseconds — where:

1. We've already done our initial `list(meetupId)` REST fetch.
2. The websocket has not yet acknowledged the subscription.
3. Someone updates a row.
4. Postgres emits the change to the publication.
5. The publication forwards it to the channel — but our channel isn't
   marked `SUBSCRIBED` yet, so the change is dropped on the floor.

The 750 ms second `emit(list(...))` is a deliberate "catch-up" fetch:
even if that race fired, by the time we re-fetch, the row is in the
DB, and we correctly update the UI.

The number is tuned high enough to comfortably outlast typical
handshake latency on cellular and the iOS simulator (which sometimes
delays websocket frames during backgrounding) without making the
initial render feel slow.

## 3 · Why the `try { ... } finally { unsubscribe }` is mandatory

A flow can be cancelled at any moment by its collector — typically
when the `viewModelScope` cancels because the ViewModel is cleared.
Without the `finally` block:

- The websocket subscription would leak.
- After enough screen visits, the device hits Supabase's per-channel
  rate limit and Realtime starts dropping events for everyone.
- The publication-side delivery queue grows.

`withContext(NonCancellable)` is mandatory because the `unsubscribe`
suspending call would itself be cancelled (we are inside a cancelled
scope) and the cleanup would never actually run on the wire.

## 4 · Why we re-fetch instead of applying diffs

`postgresChangeFlow<PostgresAction>` emits an action with a
`PostgresAction.record` (the new row) and `oldRecord` (for updates and
deletes). We could splice that into the previous list and skip the
REST round-trip.

We don't, because:

- It's significantly simpler — one code path for both initial and
  delta updates.
- For room sizes we expect (single-digit to a few hundred members), a
  `SELECT * FROM x WHERE meetup_id = ?` is cheap.
- A diff implementation has to handle reorderings, deletes that never
  arrived, and out-of-order events. The re-fetch is naturally
  consistent.

If a tab grows to thousands of rows, switch to diff-applying for that
specific repository. Don't change the template wholesale.

## 5 · Identity-aware repos use the same shape

`AppUserRepository.observeAll()` is the only observer that does **not**
filter by `meetup_id` — `app_users` is global. It still uses
`uniqueRealtimeTopic`, the catch-up emit, and the `try/finally`
unsubscribe. Read it as the canonical "global table" example.

## 6 · Adding a new realtime feed

When you add a new table that needs realtime updates:

1. Add the table to `supabase_realtime` in the migration:
   ```sql
   alter publication supabase_realtime add table public.<your_table>;
   ```
2. Add an idempotent RLS policy block (permissive for MVP, see
   `supabase/migrations/001_init.sql` and
   `supabase/migrations/006_app_users.sql` for the template).
3. Create the repository file under `commonMain/kotlin/.../<feature>/`.
4. Copy the standard template above. Substitute:
   - `uniqueRealtimeTopic("<feature>_$meetupId")` for the channel name
   - `TABLE` for your table
   - `list(meetupId)` for your REST snapshot
5. Add the realtime feed to `AppContainer` and inject it into the
   relevant ViewModel.
6. Update the relevant tab / screen to collect the flow.
7. **Document** the new feed in `docs/APP_OVERVIEW.md` under "Realtime
   feeds" and update this page if you discovered a new gotcha.

## 7 · The presence channel is the exception

`presence/GlobalPresenceTracker.kt` does NOT use `postgresChangeFlow`
— it uses Supabase Realtime **Presence**, a different mechanism that
tracks websocket-connected clients without writing to a table.

Notable differences:

- The lobby channel is **named statically** (`app_lobby`) because we
  want every device to share it — that's the whole point of presence.
- Presence callbacks must be registered **before** `subscribe()`, or
  the channel joins without `presence.enabled = true` and the server
  rejects the first `track()` call. We work around this by launching
  the `presenceChangeFlow().collect { … }` coroutine **first**, then
  `delay(150.milliseconds)` to let it register, **then** subscribing.
- `track()` is re-issued on every transition into `SUBSCRIBED` so a
  websocket reconnect (heartbeat timeout on the iOS simulator, etc.)
  doesn't leave the device invisible.
- The `clientId` used as the presence key is the install-stable id
  from `AppSettings.installClientId()`, **not** a random per-launch
  value. Without that, hot reloading the app shows the same user
  twice in the lobby until the old presence times out.

If you ever need a second presence channel, copy
`GlobalPresenceTracker.kt` wholesale. Otherwise, prefer the
`postgresChangeFlow` template for everything else.

## 8 · Repository checklist

When adding or auditing a repository's observer, check that all of
these hold:

- [ ] Channel name comes from `uniqueRealtimeTopic(base)`.
- [ ] `try { ... } finally { withContext(NonCancellable) { channel.unsubscribe() } }`
      surrounds the `subscribe()` ... `collect` ... block.
- [ ] First emit happens **after** `subscribe()` (so collectors don't
      miss the listening state).
- [ ] A second `emit(list(...))` after a 750 ms `delay` is in place
      (catch-up against the JOIN-handshake race).
- [ ] All filters use the realtime SDK's `FilterOperator.*` enum, not
      raw strings.
- [ ] The corresponding table is in the `supabase_realtime` publication
      (verify in the migration).

## Reference implementations

- `appusers/AppUserRepository.kt` — global table without `meetup_id`.
- `participants/ParticipantRepository.kt` — meetup-scoped table.
- `polls/PollRepository.kt` — multi-table snapshot (`PollBoard` joins
  three flows in one channel using `merge`).
- `presence/GlobalPresenceTracker.kt` — Presence (not `postgresChangeFlow`).
