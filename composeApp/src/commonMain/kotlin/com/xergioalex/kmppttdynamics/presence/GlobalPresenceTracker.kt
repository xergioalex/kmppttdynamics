package com.xergioalex.kmppttdynamics.presence

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeJoinsAs
import io.github.jan.supabase.realtime.decodeLeavesAs
import io.github.jan.supabase.realtime.track
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * App-wide online presence using Supabase Realtime Presence on a single
 * `app_lobby` channel. Every device that has the app open joins this
 * channel and gets counted; when the websocket disconnects the user is
 * automatically dropped, so the count is always close to "currently
 * connected" without us needing a heartbeat ourselves.
 *
 * The tracker is started once from each platform entry point so the
 * counter is alive while the user is anywhere in the app.
 *
 * ### Initialization order matters
 *
 * supabase-kt only sets `presence.enabled = true` in the channel JOIN
 * payload when a presence callback is already registered at the moment
 * `subscribe()` is called. The callback is registered lazily by
 * `presenceChangeFlow().collect { … }`. If we call `subscribe()` before
 * the collector has actually started, the server joins the channel
 * without presence, the first `track()` call errors on the server, and
 * supabase-kt logs a noisy `Received an error in channel … invalid
 * access token … rejoining` line before auto-recovering.
 *
 * To avoid that, we launch the presence collector first, give it a
 * tick to register the callback, and only then call `subscribe()`. We
 * also re-call `track()` on every SUBSCRIBED transition so a websocket
 * reconnect (common on the iOS simulator after backgrounding) doesn't
 * silently leave us invisible to other clients.
 */
class GlobalPresenceTracker(
    private val supabase: SupabaseClient,
    /**
     * Install-stable id used as the presence key. Persisted across app
     * launches via `AppSettings.installClientId()` so reconnecting after
     * a heartbeat timeout / app restart replaces the previous presence
     * record on the server instead of leaving a ghost behind for ~30 s.
     */
    private val clientId: String,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var channel: RealtimeChannel? = null
    private val members = mutableMapOf<String, PresenceMember>()

    private val _onlineCount = MutableStateFlow(1)
    val onlineCount: StateFlow<Int> = _onlineCount.asStateFlow()

    /** Idempotent: subsequent calls are no-ops while the tracker is alive. */
    fun start() {
        if (job?.isActive == true) return
        members.clear()
        job = scope.launch {
            runCatching {
                val ch = supabase.channel(CHANNEL)
                channel = ch

                // Register the presence callback first by collecting the
                // flow on a child coroutine. callbackFlow's setup runs
                // when collection starts, which is what tells the channel
                // to enable presence in its next JOIN payload.
                launch {
                    ch.presenceChangeFlow()
                        .catch { /* presence flow errors are non-fatal */ }
                        .collect { action ->
                            action.decodeJoinsAs<PresenceMember>().forEach { member ->
                                members[member.clientId] = member
                            }
                            action.decodeLeavesAs<PresenceMember>().forEach { member ->
                                members.remove(member.clientId)
                            }
                            _onlineCount.value = members.size.coerceAtLeast(1)
                        }
                }

                // (Re-)track ourselves every time the channel transitions
                // into SUBSCRIBED. This covers the initial subscribe AND
                // every websocket reconnect — without this, we'd disappear
                // from the lobby count after a heartbeat-timeout recovery.
                launch {
                    ch.status
                        .filter { it == RealtimeChannel.Status.SUBSCRIBED }
                        .collect {
                            runCatching { ch.track(PresenceMember(clientId)) }
                        }
                }

                // Give the two child coroutines above a tick to actually
                // start before we call subscribe(). Without this delay,
                // subscribe() can run first and supabase-kt joins the
                // channel without presence enabled (then auto-recovers
                // with a noisy error log).
                delay(150.milliseconds)
                ch.subscribe()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope.launch {
            withContext(NonCancellable) { runCatching { channel?.unsubscribe() } }
            channel = null
            members.clear()
            _onlineCount.value = 1
        }
    }

    @Serializable
    private data class PresenceMember(val clientId: String)

    private companion object {
        const val CHANNEL = "app_lobby"
    }
}
