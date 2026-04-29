package com.xergioalex.kmppttdynamics.trivia

import com.xergioalex.kmppttdynamics.supabase.uniqueRealtimeTopic
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

/**
 * Repository for the Kahoot-style trivia tab.
 *
 * Mirrors the `Polls` / `Raffles` pattern (one quiz row + child
 * tables, observed through realtime channels filtered by meetup or
 * quiz id). Three live-data flows expose, respectively:
 *
 * - [observeBoard] — quizzes for a meetup plus their questions and
 *   choices. Powers the host setup / lobby / question screens.
 * - [observeAnswers] — every answer for a quiz, used by the host to
 *   render the "X / N answered" counter during a question.
 * - [observeLeaderboard] — re-fetches the `trivia_leaderboard` view
 *   whenever a new answer arrives, for the post-quiz ranking.
 *
 * Every state-changing call uses guarded UPDATEs (`WHERE
 * current_question_index = expected_index`) so a doubled host click
 * or a retry doesn't double-advance the quiz.
 */
class TriviaRepository(private val supabase: SupabaseClient) {

    // ---- Quiz lifecycle --------------------------------------------------

    suspend fun createQuiz(
        meetupId: String,
        title: String,
        defaultSecondsPerQuestion: Int = 15,
        createdByClientId: String? = null,
    ): TriviaQuiz =
        supabase.from(QUIZZES)
            .insert(
                TriviaQuizDraft(
                    meetupId = meetupId,
                    title = title.trim().ifEmpty { "Trivia" },
                    defaultSecondsPerQuestion = defaultSecondsPerQuestion,
                    createdByClientId = createdByClientId,
                ),
            ) { select() }
            .decodeSingle()

    suspend fun deleteQuiz(quizId: String) {
        supabase.from(QUIZZES).delete { filter { eq("id", quizId) } }
    }

    /**
     * Open the lobby. No-op if the quiz isn't in `draft` (e.g. a
     * second host clicked "open lobby" after the first one).
     */
    suspend fun openLobby(quizId: String) {
        supabase.from(QUIZZES).update({
            set("status", TriviaStatus.LOBBY.name.lowercase())
        }) {
            filter {
                eq("id", quizId)
                eq("status", TriviaStatus.DRAFT.name.lowercase())
            }
        }
    }

    /**
     * Send the lobby back into draft so the host can keep editing.
     * Useful when the host hits "Edit" before any participant has
     * answered. No-op if the quiz already advanced.
     */
    suspend fun returnToDraft(quizId: String) {
        supabase.from(QUIZZES).update({
            set("status", TriviaStatus.DRAFT.name.lowercase())
        }) {
            filter {
                eq("id", quizId)
                eq("status", TriviaStatus.LOBBY.name.lowercase())
            }
        }
    }

    /**
     * Start the quiz at question 0. WHERE-guarded on `status='lobby'`
     * so a second tap can't restart an in-progress quiz.
     */
    suspend fun start(quizId: String) {
        val now = Clock.System.now().toString()
        supabase.from(QUIZZES).update({
            set("status", TriviaStatus.IN_PROGRESS.name.lowercase())
            set("current_question_index", 0)
            set("current_question_started_at", now)
            set("started_at", now)
        }) {
            filter {
                eq("id", quizId)
                eq("status", TriviaStatus.LOBBY.name.lowercase())
            }
        }
    }

    /**
     * Move from question [expectedIndex] to either the next question
     * or the calculating phase if [expectedIndex] is the last
     * (`expectedIndex == totalQuestions - 1`).
     *
     * The `current_question_index = expectedIndex` filter makes the
     * call idempotent — the second click silently no-ops.
     */
    suspend fun advanceQuestion(quizId: String, expectedIndex: Int, totalQuestions: Int) {
        val now = Clock.System.now().toString()
        if (expectedIndex >= totalQuestions - 1) {
            supabase.from(QUIZZES).update({
                set("status", TriviaStatus.CALCULATING.name.lowercase())
                set("calculating_started_at", now)
            }) {
                filter {
                    eq("id", quizId)
                    eq("current_question_index", expectedIndex)
                    eq("status", TriviaStatus.IN_PROGRESS.name.lowercase())
                }
            }
        } else {
            supabase.from(QUIZZES).update({
                set("current_question_index", expectedIndex + 1)
                set("current_question_started_at", now)
            }) {
                filter {
                    eq("id", quizId)
                    eq("current_question_index", expectedIndex)
                    eq("status", TriviaStatus.IN_PROGRESS.name.lowercase())
                }
            }
        }
    }

    /**
     * Closes out the calculating phase and reveals the leaderboard.
     * Idempotent — the WHERE clause keeps a flood of clients (every
     * device fires this when its 10 s suspense ends) reduced to one
     * winning UPDATE.
     */
    suspend fun finishCalculating(quizId: String) {
        supabase.from(QUIZZES).update({
            set("status", TriviaStatus.FINISHED.name.lowercase())
            set("finished_at", Clock.System.now().toString())
        }) {
            filter {
                eq("id", quizId)
                eq("status", TriviaStatus.CALCULATING.name.lowercase())
            }
        }
    }

    /**
     * Resets a finished quiz back to `draft` so the host can edit and
     * replay it. Wipes every answer; questions and choices are kept
     * so the host doesn't have to rebuild the deck.
     */
    suspend fun reset(quizId: String) {
        supabase.from(ANSWERS).delete { filter { eq("quiz_id", quizId) } }
        supabase.from(QUIZZES).update({
            set("status", TriviaStatus.DRAFT.name.lowercase())
            set("current_question_index", null as Int?)
            set("current_question_started_at", null as String?)
            set("calculating_started_at", null as String?)
            set("started_at", null as String?)
            set("finished_at", null as String?)
        }) {
            filter { eq("id", quizId) }
        }
    }

    // ---- Question / choice editing --------------------------------------

    /**
     * Adds a question. [type] selects which gameplay variant fires
     * client-side; for [TriviaQuestionType.NUMERIC] also pass
     * [expectedNumber] (and optionally [numericTolerance]) — for the
     * other three kinds those parameters are ignored on the server.
     */
    suspend fun addQuestion(
        quizId: String,
        position: Int,
        prompt: String,
        secondsToAnswer: Int,
        type: TriviaQuestionType = TriviaQuestionType.SINGLE,
        expectedNumber: Double? = null,
        numericTolerance: Double = 0.0,
    ): TriviaQuestion =
        supabase.from(QUESTIONS)
            .insert(
                TriviaQuestionDraft(
                    quizId = quizId,
                    position = position,
                    prompt = prompt.trim(),
                    secondsToAnswer = secondsToAnswer,
                    type = type,
                    expectedNumber = if (type == TriviaQuestionType.NUMERIC) expectedNumber else null,
                    numericTolerance = if (type == TriviaQuestionType.NUMERIC) numericTolerance else 0.0,
                ),
            ) { select() }
            .decodeSingle()

    suspend fun updateQuestion(
        questionId: String,
        prompt: String,
        secondsToAnswer: Int,
        type: TriviaQuestionType = TriviaQuestionType.SINGLE,
        expectedNumber: Double? = null,
        numericTolerance: Double = 0.0,
    ) {
        supabase.from(QUESTIONS).update({
            set("prompt", prompt.trim())
            set("seconds_to_answer", secondsToAnswer)
            set("question_type", type.name.lowercase())
            // For non-numeric types we explicitly null these so a
            // type-change from numeric to single doesn't leave the
            // expected_number lying around (it's harmless because
            // the trigger keys off type, but it makes the schema
            // shape easier to reason about).
            set(
                "expected_number",
                if (type == TriviaQuestionType.NUMERIC) expectedNumber else null,
            )
            set(
                "numeric_tolerance",
                if (type == TriviaQuestionType.NUMERIC) numericTolerance else 0.0,
            )
        }) { filter { eq("id", questionId) } }
    }

    suspend fun deleteQuestion(questionId: String) {
        supabase.from(QUESTIONS).delete { filter { eq("id", questionId) } }
    }

    /**
     * Replaces the choices for [questionId] in one shot: deletes
     * existing rows, inserts the new ones. Used for single, boolean
     * and multiple types; numeric questions don't have any choices
     * (the host UI never calls this for numeric).
     *
     * [correctIndices] is the set of 0-based positions that should
     * carry `is_correct=true`:
     *  - single, boolean → exactly one entry
     *  - multiple → one or more entries
     *
     * Validation is enforced visually by the host UI; the database
     * trigger handles malformed cases by returning `is_correct=false`.
     */
    suspend fun replaceChoices(
        questionId: String,
        labels: List<String>,
        correctIndices: Set<Int>,
    ) {
        supabase.from(CHOICES).delete { filter { eq("question_id", questionId) } }
        if (labels.isEmpty()) return
        val rows = labels.mapIndexed { idx, raw ->
            TriviaChoiceDraft(
                questionId = questionId,
                position = idx,
                label = raw.trim().ifEmpty { "Option ${idx + 1}" },
                isCorrect = idx in correctIndices,
            )
        }
        supabase.from(CHOICES).insert(rows)
    }

    /**
     * Convenience overload preserving the original single-correct
     * call site (used by single/boolean question editors). Same
     * server semantics as the set-based version.
     */
    suspend fun replaceChoices(questionId: String, labels: List<String>, correctIndex: Int) {
        replaceChoices(questionId, labels, setOf(correctIndex))
    }

    // ---- Enrollment (opt-in roster, mirrors raffles) --------------------

    /**
     * Adds [participantId] to the entries of [quizId]. Idempotent via
     * upsert on the `(quiz_id, participant_id)` UNIQUE — a second tap
     * (or the host bulk-enrolling someone who's already in) is a
     * silent no-op instead of a 409.
     */
    suspend fun enter(quizId: String, participantId: String, clientId: String?) {
        supabase.from(ENTRIES).upsert(
            TriviaEntryDraft(quizId, participantId, clientId),
        ) {
            onConflict = "quiz_id,participant_id"
            ignoreDuplicates = true
        }
    }

    /**
     * Bulk-enrolls every participant of [meetupId] into [quizId].
     * Convenience for the host's "Enroll everyone" button — the same
     * path participants use when they tap "Enter trivia", just batched
     * on the host's behalf so a busy meetup doesn't depend on every
     * person noticing the button.
     */
    suspend fun enrollAllParticipants(quizId: String, meetupId: String) {
        val rows = supabase.from("meetup_participants")
            .select { filter { eq("meetup_id", meetupId) } }
            .decodeList<EnrollableParticipant>()
        if (rows.isEmpty()) return
        supabase.from(ENTRIES).upsert(
            rows.map { TriviaEntryDraft(quizId, it.id, it.clientId) },
        ) {
            onConflict = "quiz_id,participant_id"
            ignoreDuplicates = true
        }
    }

    /** Removes a participant from a quiz's enrollment. Used by the
     *  "leave trivia" affordance (rare; participants usually just
     *  stay enrolled even if they walk away). */
    suspend fun leave(quizId: String, participantId: String) {
        supabase.from(ENTRIES).delete {
            filter {
                eq("quiz_id", quizId)
                eq("participant_id", participantId)
            }
        }
    }

    // ---- Answer submission ----------------------------------------------

    /**
     * Records a single-choice or boolean answer. Both kinds populate
     * `choice_id`; the question's `question_type` decides which one
     * the trigger reads it as.
     *
     * Uses upsert with `ignoreDuplicates=true` — the UNIQUE
     * `(question_id, client_id)` index makes a second tap (the user's
     * finger bounced, or the call was retried after a network blip)
     * a no-op instead of a 409.
     */
    suspend fun answer(
        quizId: String,
        questionId: String,
        choiceId: String,
        participantId: String,
        clientId: String,
    ) {
        supabase.from(ANSWERS).upsert(
            TriviaAnswerDraft(
                quizId = quizId,
                questionId = questionId,
                participantId = participantId,
                clientId = clientId,
                choiceId = choiceId,
            ),
        ) {
            onConflict = "question_id,client_id"
            ignoreDuplicates = true
        }
    }

    /**
     * Records a multiple-choice answer. The trigger compares the
     * submitted set against the canonical correct-set; partial picks
     * score zero.
     */
    suspend fun answerMultiple(
        quizId: String,
        questionId: String,
        choiceIds: List<String>,
        participantId: String,
        clientId: String,
    ) {
        supabase.from(ANSWERS).upsert(
            TriviaAnswerDraft(
                quizId = quizId,
                questionId = questionId,
                participantId = participantId,
                clientId = clientId,
                choiceIds = choiceIds,
            ),
        ) {
            onConflict = "question_id,client_id"
            ignoreDuplicates = true
        }
    }

    /**
     * Records a numeric answer. The trigger compares it against the
     * question's `expected_number` with `numeric_tolerance`.
     */
    suspend fun answerNumeric(
        quizId: String,
        questionId: String,
        value: Double,
        participantId: String,
        clientId: String,
    ) {
        supabase.from(ANSWERS).upsert(
            TriviaAnswerDraft(
                quizId = quizId,
                questionId = questionId,
                participantId = participantId,
                clientId = clientId,
                numericValue = value,
            ),
        ) {
            onConflict = "question_id,client_id"
            ignoreDuplicates = true
        }
    }

    // ---- Read helpers ---------------------------------------------------

    suspend fun listQuizzes(meetupId: String): List<TriviaQuiz> =
        supabase.from(QUIZZES)
            .select {
                filter { eq("meetup_id", meetupId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun listQuestions(quizId: String): List<TriviaQuestion> =
        supabase.from(QUESTIONS)
            .select {
                filter { eq("quiz_id", quizId) }
                order("position", Order.ASCENDING)
            }
            .decodeList()

    suspend fun listChoices(quizId: String): List<TriviaChoice> {
        val questions = listQuestions(quizId)
        if (questions.isEmpty()) return emptyList()
        val ids = questions.map { it.id }
        return supabase.from(CHOICES)
            .select {
                filter { isIn("question_id", ids) }
                order("position", Order.ASCENDING)
            }
            .decodeList()
    }

    suspend fun listAnswers(quizId: String): List<TriviaAnswer> =
        supabase.from(ANSWERS)
            .select { filter { eq("quiz_id", quizId) } }
            .decodeList()

    suspend fun listEntries(quizId: String): List<TriviaEntry> =
        supabase.from(ENTRIES)
            .select { filter { eq("quiz_id", quizId) } }
            .decodeList()

    suspend fun fetchLeaderboard(quizId: String): List<TriviaLeaderboardEntry> =
        supabase.from(LEADERBOARD)
            .select { filter { eq("quiz_id", quizId) } }
            .decodeList<TriviaLeaderboardEntry>()
            .sortedWith(
                compareByDescending<TriviaLeaderboardEntry> { it.totalPoints }
                    .thenBy { it.avgResponseMs }
                    .thenBy { it.displayName.lowercase() },
            )

    // ---- Realtime flows -------------------------------------------------

    /**
     * Realtime feed of every quiz in the meetup with its questions and
     * choices. Subscribes to:
     *
     * - `trivia_quizzes` filtered by `meetup_id`
     * - `trivia_questions` (no meetup filter — only the small
     *    relevant subset survives the snapshot re-fetch)
     * - `trivia_choices` (same)
     */
    fun observeBoard(meetupId: String): Flow<TriviaBoard> = flow {
        val channel = supabase.channel(uniqueRealtimeTopic("trivia_$meetupId"))
        val quizChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = QUIZZES
            filter("meetup_id", FilterOperator.EQ, meetupId)
        }
        val questionChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = QUESTIONS
        }
        val choiceChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = CHOICES
        }
        // Entries fire frequently during the lobby phase (one row per
        // participant per Enter / Enroll all). Subscribed here too so
        // the lobby grid + spectator-gate stay live without a second
        // observer feeding a parallel flow.
        val entryChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = ENTRIES
        }
        channel.subscribe()
        try {
            suspend fun snapshot(): TriviaBoard {
                val quizzes = listQuizzes(meetupId)
                val questionsByQuiz = mutableMapOf<String, List<TriviaQuestion>>()
                val choicesByQuestion = mutableMapOf<String, List<TriviaChoice>>()
                val entriesByQuiz = mutableMapOf<String, List<TriviaEntry>>()
                for (q in quizzes) {
                    val qs = listQuestions(q.id)
                    questionsByQuiz[q.id] = qs
                    if (qs.isNotEmpty()) {
                        val ids = qs.map { it.id }
                        val cs = supabase.from(CHOICES)
                            .select {
                                filter { isIn("question_id", ids) }
                                order("position", Order.ASCENDING)
                            }
                            .decodeList<TriviaChoice>()
                        cs.groupBy { it.questionId }.forEach { (qid, list) ->
                            choicesByQuestion[qid] = list
                        }
                    }
                    entriesByQuiz[q.id] = listEntries(q.id)
                }
                return TriviaBoard(
                    quizzes = quizzes,
                    questionsByQuiz = questionsByQuiz,
                    choicesByQuestion = choicesByQuestion,
                    entriesByQuiz = entriesByQuiz,
                )
            }
            emit(snapshot())
            merge(quizChanges, questionChanges, choiceChanges, entryChanges)
                .collect { emit(snapshot()) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    /** All answers for a quiz, refreshed on every insert. */
    fun observeAnswers(quizId: String): Flow<List<TriviaAnswer>> = flow {
        val channel = supabase.channel(uniqueRealtimeTopic("trivia_answers_$quizId"))
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = ANSWERS
            filter("quiz_id", FilterOperator.EQ, quizId)
        }
        channel.subscribe()
        try {
            emit(listAnswers(quizId))
            changes.collect { emit(listAnswers(quizId)) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    /** Re-fetches the leaderboard view on every answer-table change. */
    fun observeLeaderboard(quizId: String): Flow<List<TriviaLeaderboardEntry>> = flow {
        val channel = supabase.channel(uniqueRealtimeTopic("trivia_lb_$quizId"))
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = ANSWERS
            filter("quiz_id", FilterOperator.EQ, quizId)
        }
        channel.subscribe()
        try {
            emit(fetchLeaderboard(quizId))
            changes.collect { emit(fetchLeaderboard(quizId)) }
        } finally {
            withContext(NonCancellable) { channel.unsubscribe() }
        }
    }

    @kotlinx.serialization.Serializable
    private data class EnrollableParticipant(
        val id: String,
        @kotlinx.serialization.SerialName("client_id") val clientId: String? = null,
    )

    private companion object {
        const val QUIZZES = "trivia_quizzes"
        const val QUESTIONS = "trivia_questions"
        const val CHOICES = "trivia_choices"
        const val ANSWERS = "trivia_answers"
        const val ENTRIES = "trivia_entries"
        const val LEADERBOARD = "trivia_leaderboard"
    }
}

/**
 * Snapshot of every quiz in a meetup, plus the questions, choices and
 * enrollment rows needed to render the list and the per-screen UI.
 *
 * The [entriesByQuiz] map is the per-quiz roster used by the lobby
 * grid (avatar stack of who's in) and by [QuestionScreen]'s
 * "spectator vs answerer" gate.
 */
data class TriviaBoard(
    val quizzes: List<TriviaQuiz>,
    val questionsByQuiz: Map<String, List<TriviaQuestion>>,
    val choicesByQuestion: Map<String, List<TriviaChoice>>,
    val entriesByQuiz: Map<String, List<TriviaEntry>> = emptyMap(),
) {
    /**
     * The currently-running quiz, if any. The room can have many
     * quizzes coexisting (drafts, lobbies, finished history), but at
     * most one is "live" (in_progress or calculating). When non-null,
     * the trivia tab takes over the screen with the question /
     * calculating UI; otherwise the tab renders the list of cards.
     */
    val live: TriviaQuiz?
        get() = quizzes.firstOrNull {
            it.status == TriviaStatus.IN_PROGRESS || it.status == TriviaStatus.CALCULATING
        }
}
