package com.xergioalex.kmppttdynamics.trivia

import kotlin.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lifecycle of a Kahoot-style quiz.
 *
 * Drives every screen in the trivia tab — `TriviaTab` is essentially a
 * router on this enum. Server-authoritative: the host's device is the
 * only one that fires the transitions, but the WHERE-guarded UPDATE
 * statements in [com.xergioalex.kmppttdynamics.trivia.TriviaRepository]
 * keep the moves idempotent if two hosts race or a click is retried.
 */
@Serializable
enum class TriviaStatus {
    @SerialName("draft") DRAFT,
    @SerialName("lobby") LOBBY,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("calculating") CALCULATING,
    @SerialName("finished") FINISHED;

    val isMutableByHost: Boolean get() = this == DRAFT
    val isLive: Boolean get() = this == IN_PROGRESS || this == CALCULATING
}

@Serializable
data class TriviaQuiz(
    val id: String,
    @SerialName("meetup_id") val meetupId: String,
    val title: String,
    val status: TriviaStatus,
    @SerialName("default_seconds_per_question") val defaultSecondsPerQuestion: Int = 15,
    /**
     * 0-based index of the question currently being played. `null` while
     * the quiz is in `draft` or `lobby`. Server-authoritative.
     */
    @SerialName("current_question_index") val currentQuestionIndex: Int? = null,
    /**
     * Server timestamp when the host advanced into the current
     * question. The single source of truth for every device's
     * countdown timer (each client computes
     * `deadline - Clock.System.now()` locally).
     */
    @SerialName("current_question_started_at") val currentQuestionStartedAt: Instant? = null,
    /** Set when the quiz transitions into `calculating`. The 10 s
     *  suspense window is `Clock.System.now() - calculatingStartedAt`. */
    @SerialName("calculating_started_at") val calculatingStartedAt: Instant? = null,
    @SerialName("created_by_client_id") val createdByClientId: String? = null,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("started_at") val startedAt: Instant? = null,
    @SerialName("finished_at") val finishedAt: Instant? = null,
)

/**
 * Insert payload for a new quiz.
 *
 * `status` and `defaultSecondsPerQuestion` carry @EncodeDefault so the
 * row is created with the values we expect even when the Kotlin
 * defaults match — without it kotlinx.serialization would drop them
 * and the Postgres column defaults would win, which is the bug that
 * left every freshly created poll/raffle stuck on `draft`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TriviaQuizDraft(
    @SerialName("meetup_id") val meetupId: String,
    val title: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val status: TriviaStatus = TriviaStatus.DRAFT,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("default_seconds_per_question") val defaultSecondsPerQuestion: Int = 15,
    @SerialName("created_by_client_id") val createdByClientId: String? = null,
)

@Serializable
data class TriviaQuestion(
    val id: String,
    @SerialName("quiz_id") val quizId: String,
    val position: Int,
    val prompt: String,
    @SerialName("seconds_to_answer") val secondsToAnswer: Int = 15,
    @SerialName("created_at") val createdAt: Instant,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TriviaQuestionDraft(
    @SerialName("quiz_id") val quizId: String,
    val position: Int,
    val prompt: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("seconds_to_answer") val secondsToAnswer: Int = 15,
)

@Serializable
data class TriviaChoice(
    val id: String,
    @SerialName("question_id") val questionId: String,
    val position: Int,
    val label: String,
    @SerialName("is_correct") val isCorrect: Boolean = false,
    @SerialName("created_at") val createdAt: Instant,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TriviaChoiceDraft(
    @SerialName("question_id") val questionId: String,
    val position: Int,
    val label: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("is_correct") val isCorrect: Boolean = false,
)

/**
 * One participant's answer to one question. Server-side fields
 * (`isCorrect`, `responseMs`, `pointsAwarded`) are populated by the
 * `trivia_answers_compute_score` BEFORE INSERT trigger; the client
 * only sends the four-tuple `(quiz, question, choice, participant)`.
 */
@Serializable
data class TriviaAnswer(
    val id: String,
    @SerialName("quiz_id") val quizId: String,
    @SerialName("question_id") val questionId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("choice_id") val choiceId: String,
    @SerialName("is_correct") val isCorrect: Boolean = false,
    @SerialName("response_ms") val responseMs: Int = 0,
    @SerialName("points_awarded") val pointsAwarded: Int = 0,
    @SerialName("answered_at") val answeredAt: Instant,
)

@Serializable
data class TriviaAnswerDraft(
    @SerialName("quiz_id") val quizId: String,
    @SerialName("question_id") val questionId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("choice_id") val choiceId: String,
)

/**
 * Per-quiz enrollment row. Mirrors `raffle_entries` exactly: a
 * participant explicitly opts into a trivia (or the host bulk-enrolls
 * the whole room) and that's the gate for whether the choice buttons
 * are tappable when the round starts.
 *
 * `client_id` is denormalized from the linked
 * `meetup_participants.client_id` so leaderboard / enrollment queries
 * that join straight to `app_users` (avatar / display name) stay a
 * single hop.
 */
@Serializable
data class TriviaEntry(
    val id: String,
    @SerialName("quiz_id") val quizId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("created_at") val createdAt: Instant,
)

@Serializable
data class TriviaEntryDraft(
    @SerialName("quiz_id") val quizId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("client_id") val clientId: String? = null,
)

/**
 * One row of the `trivia_leaderboard` view. The view does the heavy
 * aggregation server-side; the client just sorts the result by
 * `total_points DESC`, then `avg_response_ms ASC` for tie-breaking,
 * then `display_name ASC` for full determinism.
 */
@Serializable
data class TriviaLeaderboardEntry(
    @SerialName("quiz_id") val quizId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_id") val avatarId: Int? = null,
    @SerialName("total_points") val totalPoints: Int,
    @SerialName("correct_count") val correctCount: Int,
    @SerialName("answered_count") val answeredCount: Int,
    @SerialName("avg_response_ms") val avgResponseMs: Int,
)
