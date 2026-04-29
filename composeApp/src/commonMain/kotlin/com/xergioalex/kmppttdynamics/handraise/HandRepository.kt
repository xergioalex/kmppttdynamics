package com.xergioalex.kmppttdynamics.handraise

import com.xergioalex.kmppttdynamics.domain.HandStatus
import com.xergioalex.kmppttdynamics.domain.RaiseHandDraft
import com.xergioalex.kmppttdynamics.domain.RaisedHand
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import com.xergioalex.kmppttdynamics.supabase.uniqueRealtimeTopic
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class HandRepository(private val supabase: SupabaseClient) {

    suspend fun raise(meetupId: String, participantId: String, message: String? = null): RaisedHand =
        supabase.from(TABLE)
            .insert(RaiseHandDraft(meetupId, participantId, message?.trim()?.ifBlank { null })) { select() }
            .decodeSingle()

    suspend fun acknowledge(handId: String) {
        supabase.from(TABLE)
            .update(
                mapOf(
                    "status" to HandStatus.ACKNOWLEDGED.name.lowercase(),
                    "acknowledged_at" to Clock.System.now().toString(),
                ),
            ) { filter { eq("id", handId) } }
    }

    suspend fun setSpeaking(handId: String) {
        supabase.from(TABLE)
            .update(mapOf("status" to HandStatus.SPEAKING.name.lowercase())) {
                filter { eq("id", handId) }
            }
    }

    suspend fun lower(handId: String) {
        supabase.from(TABLE)
            .update(
                mapOf(
                    "status" to HandStatus.LOWERED.name.lowercase(),
                    "lowered_at" to Clock.System.now().toString(),
                ),
            ) { filter { eq("id", handId) } }
    }

    suspend fun dismiss(handId: String) {
        supabase.from(TABLE)
            .update(
                mapOf(
                    "status" to HandStatus.DISMISSED.name.lowercase(),
                    "lowered_at" to Clock.System.now().toString(),
                ),
            ) { filter { eq("id", handId) } }
    }

    /**
     * Host-only sweep: lowers every active hand in the meetup in a
     * single round-trip. Filters by the three "active" statuses so we
     * don't disturb already-lowered or dismissed history.
     */
    suspend fun clearAll(meetupId: String) {
        supabase.from(TABLE)
            .update(
                mapOf(
                    "status" to HandStatus.LOWERED.name.lowercase(),
                    "lowered_at" to Clock.System.now().toString(),
                ),
            ) {
                filter {
                    eq("meetup_id", meetupId)
                    isIn(
                        "status",
                        listOf(
                            HandStatus.RAISED.name.lowercase(),
                            HandStatus.ACKNOWLEDGED.name.lowercase(),
                            HandStatus.SPEAKING.name.lowercase(),
                        ),
                    )
                }
            }
    }

    /** Active-only list for the host queue, oldest raise first. */
    suspend fun listActive(meetupId: String): List<RaisedHand> =
        supabase.from(TABLE)
            .select {
                filter {
                    eq("meetup_id", meetupId)
                    isIn(
                        "status",
                        listOf(
                            HandStatus.RAISED.name.lowercase(),
                            HandStatus.ACKNOWLEDGED.name.lowercase(),
                            HandStatus.SPEAKING.name.lowercase(),
                        ),
                    )
                }
                order("raised_at", Order.ASCENDING)
            }
            .decodeList()

    fun observe(meetupId: String): Flow<List<RaisedHand>> = flow {
        val channel = supabase.channel(uniqueRealtimeTopic("hands_$meetupId"))
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
            filter("meetup_id", FilterOperator.EQ, meetupId)
        }
        channel.subscribe()
        try {
            emit(listActive(meetupId))
            changes.collect { emit(listActive(meetupId)) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    private companion object {
        const val TABLE = "raised_hands"
    }
}
