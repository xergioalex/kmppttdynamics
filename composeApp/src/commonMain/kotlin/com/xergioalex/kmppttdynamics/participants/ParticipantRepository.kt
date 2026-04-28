package com.xergioalex.kmppttdynamics.participants

import com.xergioalex.kmppttdynamics.domain.JoinRequest
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import com.xergioalex.kmppttdynamics.supabase.uniqueRealtimeTopic
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ParticipantRepository(private val supabase: SupabaseClient) {

    /**
     * Idempotently joins [clientId] (the device's install-stable id) into
     * [meetupId] with the given [displayName] and [role].
     *
     * - If a row already exists for `(meetup_id, client_id)`, refreshes
     *   `display_name`, `is_online = true`, and `last_seen_at`, but
     *   preserves the existing `role`. This means a host who left and
     *   came back does **not** get demoted to participant just because
     *   the join screen passes `PARTICIPANT` by default — their host
     *   role survives.
     * - Otherwise inserts a new row with the requested role.
     *
     * The Postgres `(meetup_id, client_id)` unique partial index is the
     * hard guarantee that this is per-device idempotent. The find-first
     * pattern is layered on top so `role` doesn't get clobbered on a
     * conflict.
     */
    suspend fun join(
        meetupId: String,
        displayName: String,
        role: ParticipantRole,
        clientId: String,
    ): MeetupParticipant {
        val existing = findByClientId(meetupId, clientId)
        if (existing != null) {
            return supabase.from(TABLE)
                .update(
                    mapOf(
                        "display_name" to displayName.trim(),
                        "is_online" to true,
                        "last_seen_at" to Clock.System.now().toString(),
                    ),
                ) {
                    select()
                    filter { eq("id", existing.id) }
                }
                .decodeSingle()
        }
        return supabase.from(TABLE)
            .insert(JoinRequest(meetupId, displayName.trim(), role, clientId)) { select() }
            .decodeSingle()
    }

    /** Looks up the row this device owns in [meetupId]. Returns null when none exists yet. */
    suspend fun findByClientId(meetupId: String, clientId: String): MeetupParticipant? =
        supabase.from(TABLE)
            .select {
                filter {
                    eq("meetup_id", meetupId)
                    eq("client_id", clientId)
                }
            }
            .decodeList<MeetupParticipant>()
            .firstOrNull()

    suspend fun findById(participantId: String): MeetupParticipant? =
        supabase.from(TABLE)
            .select { filter { eq("id", participantId) } }
            .decodeSingleOrNull<MeetupParticipant>()

    /**
     * Locks a pre-migration participant row (one whose `client_id`
     * column is still NULL) onto this device's [clientId], so future
     * lookups via [findByClientId] find it without needing the local
     * AppSettings cache. Best-effort: if the row was already claimed
     * by someone else, the update returns nothing and we leave the
     * row alone.
     */
    suspend fun claim(participantId: String, clientId: String): MeetupParticipant? =
        supabase.from(TABLE)
            .update(
                mapOf(
                    "client_id" to clientId,
                    "is_online" to true,
                    "last_seen_at" to Clock.System.now().toString(),
                ),
            ) {
                select()
                filter {
                    eq("id", participantId)
                    exact("client_id", null)
                }
            }
            .decodeSingleOrNull<MeetupParticipant>()

    suspend fun setOnline(participantId: String, online: Boolean) {
        supabase.from(TABLE)
            .update(
                mapOf(
                    "is_online" to online,
                    "last_seen_at" to Clock.System.now().toString(),
                ),
            ) {
                filter { eq("id", participantId) }
            }
    }

    /** Updates a participant's role (host can promote/demote). */
    suspend fun setRole(participantId: String, role: ParticipantRole) {
        supabase.from(TABLE)
            .update(mapOf("role" to role.name.lowercase())) {
                filter { eq("id", participantId) }
            }
    }

    suspend fun delete(participantId: String) {
        supabase.from(TABLE).delete { filter { eq("id", participantId) } }
    }

    suspend fun list(meetupId: String): List<MeetupParticipant> =
        supabase.from(TABLE)
            .select {
                filter { eq("meetup_id", meetupId) }
                order("joined_at", Order.ASCENDING)
            }
            .decodeList()

    /**
     * Realtime list of participants for [meetupId]. On every insert /
     * update / delete to the participants table for that meetup, re-fetches
     * the row set and emits it.
     *
     * The flow guards against the classic Supabase Realtime race where
     * `channel.subscribe()` returns before the websocket has finished
     * acknowledging the subscription. We re-fetch a second time after a
     * short delay so any updates that happened between `subscribe()` and
     * the actual subscription handshake (e.g. our own `is_online = true`
     * write right after navigating into the room) still show up.
     */
    fun observe(meetupId: String): Flow<List<MeetupParticipant>> = flow {
        val channel = supabase.channel(uniqueRealtimeTopic("participants_$meetupId"))
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
            filter("meetup_id", FilterOperator.EQ, meetupId)
        }
        channel.subscribe()
        try {
            emit(list(meetupId))
            kotlinx.coroutines.delay(750)
            emit(list(meetupId))
            changes.collect { emit(list(meetupId)) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    private companion object {
        const val TABLE = "meetup_participants"
    }
}
