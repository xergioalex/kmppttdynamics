package com.xergioalex.kmppttdynamics.polls

import com.xergioalex.kmppttdynamics.domain.Poll
import com.xergioalex.kmppttdynamics.domain.PollDraft
import com.xergioalex.kmppttdynamics.domain.PollOption
import com.xergioalex.kmppttdynamics.domain.PollOptionDraft
import com.xergioalex.kmppttdynamics.domain.PollStatus
import com.xergioalex.kmppttdynamics.domain.PollVote
import com.xergioalex.kmppttdynamics.domain.PollVoteDraft
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

class PollRepository(private val supabase: SupabaseClient) {

    /** Creates a poll plus its options atomically (best-effort: two inserts,
     *  the second decodes the resulting options). */
    suspend fun create(
        meetupId: String,
        hostParticipantId: String,
        question: String,
        options: List<String>,
        isAnonymous: Boolean = true,
    ): Poll {
        val cleanedOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
        require(cleanedOptions.size >= 2) { "A poll needs at least two options." }

        val poll: Poll = supabase.from(POLLS)
            .insert(
                PollDraft(
                    meetupId = meetupId,
                    createdBy = hostParticipantId,
                    question = question.trim(),
                    status = PollStatus.OPEN,
                    isAnonymous = isAnonymous,
                ),
            ) { select() }
            .decodeSingle()
        supabase.from(OPTIONS).insert(
            cleanedOptions.mapIndexed { idx, text ->
                PollOptionDraft(pollId = poll.id, text = text, position = idx)
            },
        )
        return poll
    }

    suspend fun close(pollId: String) {
        supabase.from(POLLS)
            .update(
                mapOf(
                    "status" to PollStatus.CLOSED.name.lowercase(),
                    "closed_at" to Clock.System.now().toString(),
                ),
            ) { filter { eq("id", pollId) } }
    }

    /**
     * One vote per participant per poll. Re-voting overrides the
     * previous choice. Implemented as upsert on the
     * `(poll_id, participant_id)` unique index so changing the vote
     * is one round trip instead of two.
     */
    suspend fun vote(pollId: String, optionId: String, participantId: String) {
        supabase.from(VOTES).upsert(PollVoteDraft(pollId, optionId, participantId)) {
            onConflict = "poll_id,participant_id"
        }
    }

    suspend fun listPolls(meetupId: String): List<Poll> =
        supabase.from(POLLS)
            .select {
                filter {
                    eq("meetup_id", meetupId)
                    neq("status", PollStatus.ARCHIVED.name.lowercase())
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun listOptions(pollId: String): List<PollOption> =
        supabase.from(OPTIONS)
            .select {
                filter { eq("poll_id", pollId) }
                order("position", Order.ASCENDING)
            }
            .decodeList()

    suspend fun listVotes(pollId: String): List<PollVote> =
        supabase.from(VOTES)
            .select { filter { eq("poll_id", pollId) } }
            .decodeList()

    /** Realtime feed of (poll → options → votes) snapshots for the meetup. */
    fun observeBoard(meetupId: String): Flow<PollBoard> = flow {
        val channel = supabase.channel(uniqueRealtimeTopic("polls_$meetupId"))
        val pollChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = POLLS
            filter("meetup_id", FilterOperator.EQ, meetupId)
        }
        // Options + votes don't carry meetup_id; subscribe globally and we
        // re-fetch on any change. With a small number of polls per room
        // this is simple and correct.
        val voteChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = VOTES
        }
        val optionChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = OPTIONS
        }
        channel.subscribe()
        try {
            suspend fun snapshot(): PollBoard {
                val polls = listPolls(meetupId)
                val options = polls.associate { it.id to listOptions(it.id) }
                val votes = polls.associate { it.id to listVotes(it.id) }
                return PollBoard(polls, options, votes)
            }
            emit(snapshot())
            kotlinx.coroutines.flow.merge(pollChanges, voteChanges, optionChanges)
                .collect { emit(snapshot()) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    private companion object {
        const val POLLS = "polls"
        const val OPTIONS = "poll_options"
        const val VOTES = "poll_votes"
    }
}

/** Snapshot of all polls in a meetup with their options and votes. */
data class PollBoard(
    val polls: List<Poll>,
    val options: Map<String, List<PollOption>>,
    val votes: Map<String, List<PollVote>>,
)
