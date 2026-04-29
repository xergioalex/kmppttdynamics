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

/**
 * The four question kinds the host can author. Each has a different
 * editor variant on [com.xergioalex.kmppttdynamics.ui.room.tabs.trivia.HostSetupScreen]
 * and a different gameplay variant on
 * [com.xergioalex.kmppttdynamics.ui.room.tabs.trivia.QuestionScreen].
 *
 * Server-side correctness checking lives in the
 * `trivia_compute_answer_score` Postgres trigger:
 *
 *  - [SINGLE], [BOOLEAN] — read `choice_id` and look up
 *    `trivia_choices.is_correct`. Boolean rides the same plumbing
 *    as single-choice but with exactly two pre-baked choices
 *    ("True", "False"); the only difference is UI presentation.
 *  - [MULTIPLE] — compare the submitted `choice_ids uuid[]` set to
 *    the set of `is_correct=true` choices. All-or-nothing: missing
 *    or extra picks = wrong.
 *  - [NUMERIC] — compare `numeric_value` against the question's
 *    `expected_number` with `numeric_tolerance`.
 */
@Serializable
enum class TriviaQuestionType {
    @SerialName("single") SINGLE,
    @SerialName("boolean") BOOLEAN,
    @SerialName("multiple") MULTIPLE,
    @SerialName("numeric") NUMERIC,
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
    /**
     * What kind of question this is. Defaults to [TriviaQuestionType.SINGLE]
     * so legacy rows authored before migration 009 keep behaving like
     * the original Kahoot-style single-choice card.
     */
    @SerialName("question_type") val type: TriviaQuestionType = TriviaQuestionType.SINGLE,
    /**
     * Only populated for [TriviaQuestionType.NUMERIC]. The "correct"
     * value the client must match within [numericTolerance].
     */
    @SerialName("expected_number") val expectedNumber: Double? = null,
    /**
     * Only meaningful for [TriviaQuestionType.NUMERIC]. Allowed
     * deviation from [expectedNumber]. Defaults to 0 (exact match).
     */
    @SerialName("numeric_tolerance") val numericTolerance: Double = 0.0,
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
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("question_type") val type: TriviaQuestionType = TriviaQuestionType.SINGLE,
    @SerialName("expected_number") val expectedNumber: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("numeric_tolerance") val numericTolerance: Double = 0.0,
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
 * sends exactly one of [choiceId] / [choiceIds] / [numericValue]
 * depending on the question's [TriviaQuestionType].
 */
@Serializable
data class TriviaAnswer(
    val id: String,
    @SerialName("quiz_id") val quizId: String,
    @SerialName("question_id") val questionId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("client_id") val clientId: String,
    /** Single / boolean: the chosen choice id. Null otherwise. */
    @SerialName("choice_id") val choiceId: String? = null,
    /** Multiple-choice: every chosen choice id. Null otherwise. */
    @SerialName("choice_ids") val choiceIds: List<String>? = null,
    /** Numeric: the value the user typed. Null otherwise. */
    @SerialName("numeric_value") val numericValue: Double? = null,
    @SerialName("is_correct") val isCorrect: Boolean = false,
    @SerialName("response_ms") val responseMs: Int = 0,
    @SerialName("points_awarded") val pointsAwarded: Int = 0,
    @SerialName("answered_at") val answeredAt: Instant,
)

/**
 * Insert payload for an answer. Use the matching constructor variant
 * via the helpers on [com.xergioalex.kmppttdynamics.trivia.TriviaRepository].
 *
 * `explicit_nulls = false` on the JSON instance drops the unused two
 * columns out of the wire payload, so a single-choice answer doesn't
 * accidentally clear `numeric_value` on a row where someone else
 * already typed it (in practice impossible because `(question_id,
 * client_id)` is UNIQUE — but the smaller payload is also nicer).
 */
@Serializable
data class TriviaAnswerDraft(
    @SerialName("quiz_id") val quizId: String,
    @SerialName("question_id") val questionId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("choice_id") val choiceId: String? = null,
    @SerialName("choice_ids") val choiceIds: List<String>? = null,
    @SerialName("numeric_value") val numericValue: Double? = null,
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
