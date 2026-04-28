package com.xergioalex.kmppttdynamics.participants

import com.xergioalex.kmppttdynamics.domain.JoinRequest
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ParticipantRepository(private val supabase: SupabaseClient) {

    suspend fun join(meetupId: String, displayName: String, role: ParticipantRole): MeetupParticipant =
        supabase.from(TABLE)
            .insert(JoinRequest(meetupId, displayName.trim(), role)) { select() }
            .decodeSingle()

    suspend fun setOnline(participantId: String, online: Boolean) {
        supabase.from(TABLE)
            .update(mapOf("is_online" to online)) {
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
     * the row set and emits it. Simple, correct, and fast enough for the
     * room sizes we expect.
     */
    fun observe(meetupId: String): Flow<List<MeetupParticipant>> = flow {
        val channel = supabase.channel("participants_$meetupId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
            filter("meetup_id", FilterOperator.EQ, meetupId)
        }
        channel.subscribe()
        try {
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
