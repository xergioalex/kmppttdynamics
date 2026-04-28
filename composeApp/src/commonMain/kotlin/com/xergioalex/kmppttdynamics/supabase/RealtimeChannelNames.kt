package com.xergioalex.kmppttdynamics.supabase

import kotlin.random.Random

/**
 * Returns a unique channel topic name built from [base] and an 8-char
 * random hex suffix.
 *
 * Why this exists: in supabase-kt, two callers that ask for
 * `supabase.channel("X")` get the SAME `RealtimeChannel` instance back
 * from the plugin's subscription map. When the first caller's flow
 * collector ends and runs its `unsubscribe()` in `finally`, the
 * underlying channel is torn down — silencing every other caller that
 * is still listening on the same name.
 *
 * That's how the avatar picker stopped seeing realtime updates after
 * the user re-opened it: a previous `OnboardingViewModel` (or
 * `RoomViewModel`'s `users.observeAll()`) had already unsubscribed
 * the shared `app_users` channel. Suffixing the topic per-invocation
 * gives each consumer its own channel and isolates them.
 */
internal fun uniqueRealtimeTopic(base: String): String {
    val suffix = (1..8).map { Random.nextInt(0, 16).toString(16) }.joinToString("")
    return "${base}_$suffix"
}
