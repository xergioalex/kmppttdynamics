package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("participant_id") val participantId: String? = null,
    val question: String,
    val status: QuestionStatus,
    @SerialName("upvotes_count") val upvotesCount: Int = 0,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("answered_at") val answeredAt: Instant? = null,
)

@Serializable
enum class QuestionStatus {
    @SerialName("open") OPEN,
    @SerialName("answered") ANSWERED,
    @SerialName("hidden") HIDDEN,
}

@Serializable
data class QuestionDraft(
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("participant_id") val participantId: String? = null,
    val question: String,
)

/** A single upvote. The `unique(question_id, participant_id)` constraint
 *  on the table prevents duplicates server-side. */
@Serializable
data class QuestionVote(
    val id: String,
    @SerialName("question_id") val questionId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("created_at") val createdAt: Instant,
)

@Serializable
data class QuestionVoteDraft(
    @SerialName("question_id") val questionId: String,
    @SerialName("participant_id") val participantId: String,
)
