package com.xergioalex.kmppttdynamics.qa

import com.xergioalex.kmppttdynamics.domain.Question
import com.xergioalex.kmppttdynamics.domain.QuestionDraft
import com.xergioalex.kmppttdynamics.domain.QuestionStatus
import com.xergioalex.kmppttdynamics.domain.QuestionVote
import com.xergioalex.kmppttdynamics.domain.QuestionVoteDraft
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
import kotlin.time.Clock

class QuestionRepository(private val supabase: SupabaseClient) {

    suspend fun ask(meetupId: String, participantId: String, text: String): Question =
        supabase.from(QUESTIONS)
            .insert(QuestionDraft(meetupId, participantId, text.trim())) { select() }
            .decodeSingle()

    suspend fun upvote(questionId: String, participantId: String) {
        // The unique(question_id, participant_id) index makes a duplicate
        // insert raise 23505. We swallow it so the UI can be optimistic.
        runCatching {
            supabase.from(VOTES).insert(QuestionVoteDraft(questionId, participantId))
        }
    }

    suspend fun unvote(questionId: String, participantId: String) {
        supabase.from(VOTES).delete {
            filter {
                eq("question_id", questionId)
                eq("participant_id", participantId)
            }
        }
    }

    suspend fun markAnswered(questionId: String) {
        supabase.from(QUESTIONS)
            .update(
                mapOf(
                    "status" to QuestionStatus.ANSWERED.name.lowercase(),
                    "answered_at" to Clock.System.now().toString(),
                ),
            ) { filter { eq("id", questionId) } }
    }

    suspend fun hide(questionId: String) {
        supabase.from(QUESTIONS)
            .update(mapOf("status" to QuestionStatus.HIDDEN.name.lowercase())) {
                filter { eq("id", questionId) }
            }
    }

    suspend fun list(meetupId: String): List<Question> =
        supabase.from(QUESTIONS)
            .select {
                filter {
                    eq("meetup_id", meetupId)
                    neq("status", QuestionStatus.HIDDEN.name.lowercase())
                }
                order("upvotes_count", Order.DESCENDING)
                order("created_at", Order.ASCENDING)
            }
            .decodeList()

    /** Question IDs the given participant has upvoted, used to colour the upvote button. */
    suspend fun listMyVotes(meetupId: String, participantId: String): Set<String> {
        val questions = list(meetupId).map { it.id }
        if (questions.isEmpty()) return emptySet()
        return supabase.from(VOTES)
            .select {
                filter {
                    eq("participant_id", participantId)
                    isIn("question_id", questions)
                }
            }
            .decodeList<QuestionVote>()
            .map { it.questionId }
            .toSet()
    }

    fun observe(meetupId: String): Flow<List<Question>> = flow {
        val channel = supabase.channel("questions_$meetupId")
        val questionChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = QUESTIONS
            filter("meetup_id", FilterOperator.EQ, meetupId)
        }
        // Votes don't have a meetup_id column, but the questions trigger
        // updates upvotes_count on its row, so listening to QUESTIONS
        // alone is sufficient — the trigger fires the UPDATE that we
        // already subscribe to.
        channel.subscribe()
        try {
            emit(list(meetupId))
            questionChanges.collect { emit(list(meetupId)) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    private companion object {
        const val QUESTIONS = "questions"
        const val VOTES = "question_votes"
    }
}
