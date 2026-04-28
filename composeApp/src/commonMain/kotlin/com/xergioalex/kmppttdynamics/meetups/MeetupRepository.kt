package com.xergioalex.kmppttdynamics.meetups

import com.xergioalex.kmppttdynamics.JoinCodeGenerator
import com.xergioalex.kmppttdynamics.domain.Meetup
import com.xergioalex.kmppttdynamics.domain.MeetupDraft
import com.xergioalex.kmppttdynamics.domain.MeetupStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import com.xergioalex.kmppttdynamics.supabase.uniqueRealtimeTopic
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class MeetupRepository(private val supabase: SupabaseClient) {

    /**
     * Creates a meetup. If [draft]'s join_code is empty we generate one.
     * Retries up to a few times if the random code collides with an
     * existing row (the `join_code` column is unique).
     */
    suspend fun create(draft: MeetupDraft): Meetup {
        var lastError: Throwable? = null
        repeat(MAX_CODE_RETRIES) {
            val candidate = if (draft.joinCode.isBlank()) {
                draft.copy(joinCode = JoinCodeGenerator.generate())
            } else {
                draft
            }
            try {
                return supabase.from(TABLE)
                    .insert(candidate) { select() }
                    .decodeSingle()
            } catch (t: Throwable) {
                lastError = t
                if (draft.joinCode.isNotBlank()) throw t // user-supplied code → don't retry
            }
        }
        throw lastError ?: IllegalStateException("Could not allocate a unique join code")
    }

    /** Returns the meetup with that join code, or null. */
    suspend fun findByJoinCode(joinCode: String): Meetup? =
        supabase.from(TABLE)
            .select { filter { eq("join_code", joinCode.uppercase()) } }
            .decodeList<Meetup>()
            .firstOrNull()

    suspend fun findById(id: String): Meetup =
        supabase.from(TABLE)
            .select { filter { eq("id", id) } }
            .decodeSingle()

    suspend fun updateStatus(id: String, status: MeetupStatus): Meetup =
        supabase.from(TABLE)
            .update(mapOf("status" to status.name.lowercase())) {
                select()
                filter { eq("id", id) }
            }
            .decodeSingle()

    /** Snapshot of all live meetups (status = live | paused), newest first. */
    suspend fun listLive(): List<Meetup> =
        supabase.from(TABLE)
            .select {
                filter { isIn("status", listOf("live", "paused")) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun listPast(limit: Long = 20): List<Meetup> =
        supabase.from(TABLE)
            .select {
                filter { isIn("status", listOf("ended", "archived")) }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    /**
     * Re-fetches the live + past meetup lists every time any row changes
     * in `meetups`. Good enough for a home screen; more efficient diffing
     * can come later.
     */
    fun observeAll(): Flow<HomeFeed> = flow {
        val channel = supabase.channel(uniqueRealtimeTopic("home_meetups"))
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
        }
        channel.subscribe()
        try {
            emit(HomeFeed(live = listLive(), past = listPast()))
            changes.collect {
                emit(HomeFeed(live = listLive(), past = listPast()))
            }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    private companion object {
        const val TABLE = "meetups"
        const val MAX_CODE_RETRIES = 5
    }
}

/** Snapshot of the home screen content. */
data class HomeFeed(
    val live: List<Meetup>,
    val past: List<Meetup>,
)
