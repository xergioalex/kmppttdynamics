package com.xergioalex.kmppttdynamics.raffles

import com.xergioalex.kmppttdynamics.domain.Raffle
import com.xergioalex.kmppttdynamics.domain.RaffleDraft
import com.xergioalex.kmppttdynamics.domain.RaffleEntry
import com.xergioalex.kmppttdynamics.domain.RaffleEntryDraft
import com.xergioalex.kmppttdynamics.domain.RaffleStatus
import com.xergioalex.kmppttdynamics.domain.RaffleWinner
import com.xergioalex.kmppttdynamics.domain.RaffleWinnerDraft
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.random.Random

class RaffleRepository(private val supabase: SupabaseClient) {

    suspend fun create(meetupId: String, hostParticipantId: String, title: String): Raffle =
        supabase.from(RAFFLES)
            .insert(
                RaffleDraft(
                    meetupId = meetupId,
                    createdBy = hostParticipantId,
                    title = title.trim(),
                    status = RaffleStatus.OPEN,
                ),
            ) { select() }
            .decodeSingle()

    suspend fun enter(raffleId: String, participantId: String) {
        runCatching {
            supabase.from(ENTRIES).insert(RaffleEntryDraft(raffleId, participantId))
        }
    }

    /** Bulk-enroll every participant of [meetupId] into [raffleId]. Useful
     *  when the host says "everyone in the room is in". */
    suspend fun enrollAllParticipants(raffleId: String, meetupId: String) {
        val rows = supabase.from("meetup_participants")
            .select { filter { eq("meetup_id", meetupId) } }
            .decodeList<Participant>()
        if (rows.isEmpty()) return
        supabase.from(ENTRIES)
            .insert(rows.map { RaffleEntryDraft(raffleId, it.id) })
    }

    /**
     * Picks a random entry client-side and writes the winner. For
     * production this should move into a SECURITY DEFINER SQL function
     * or an Edge Function — see SECURITY.md.
     */
    suspend fun drawWinner(raffleId: String, random: Random = Random.Default): RaffleWinner? {
        val entries = listEntries(raffleId)
        if (entries.isEmpty()) return null
        val pick = entries[random.nextInt(entries.size)]
        val winner: RaffleWinner = supabase.from(WINNERS)
            .insert(RaffleWinnerDraft(raffleId, pick.participantId)) { select() }
            .decodeSingle()
        supabase.from(RAFFLES)
            .update(
                mapOf(
                    "status" to RaffleStatus.DRAWN.name.lowercase(),
                    "drawn_at" to Clock.System.now().toString(),
                ),
            ) { filter { eq("id", raffleId) } }
        return winner
    }

    suspend fun close(raffleId: String) {
        supabase.from(RAFFLES)
            .update(
                mapOf(
                    "status" to RaffleStatus.CLOSED.name.lowercase(),
                    "closed_at" to Clock.System.now().toString(),
                ),
            ) { filter { eq("id", raffleId) } }
    }

    suspend fun list(meetupId: String): List<Raffle> =
        supabase.from(RAFFLES)
            .select {
                filter {
                    eq("meetup_id", meetupId)
                    neq("status", RaffleStatus.ARCHIVED.name.lowercase())
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun listEntries(raffleId: String): List<RaffleEntry> =
        supabase.from(ENTRIES)
            .select { filter { eq("raffle_id", raffleId) } }
            .decodeList()

    suspend fun listWinners(raffleId: String): List<RaffleWinner> =
        supabase.from(WINNERS)
            .select {
                filter { eq("raffle_id", raffleId) }
                order("drawn_at", Order.DESCENDING)
            }
            .decodeList()

    fun observeBoard(meetupId: String): Flow<RaffleBoard> = flow {
        val channel = supabase.channel("raffles_$meetupId")
        val raffleChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = RAFFLES
            filter("meetup_id", FilterOperator.EQ, meetupId)
        }
        val entryChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = ENTRIES
        }
        val winnerChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = WINNERS
        }
        channel.subscribe()
        try {
            suspend fun snapshot(): RaffleBoard {
                val raffles = list(meetupId)
                val entries = raffles.associate { it.id to listEntries(it.id) }
                val winners = raffles.associate { it.id to listWinners(it.id) }
                return RaffleBoard(raffles, entries, winners)
            }
            emit(snapshot())
            merge(raffleChanges, entryChanges, winnerChanges)
                .collect { emit(snapshot()) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    @kotlinx.serialization.Serializable
    private data class Participant(val id: String)

    private companion object {
        const val RAFFLES = "raffles"
        const val ENTRIES = "raffle_entries"
        const val WINNERS = "raffle_winners"
    }
}

data class RaffleBoard(
    val raffles: List<Raffle>,
    val entries: Map<String, List<RaffleEntry>>,
    val winners: Map<String, List<RaffleWinner>>,
)
