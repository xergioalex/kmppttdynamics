package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Poll(
    val id: String,
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("created_by") val createdBy: String? = null,
    val question: String,
    val status: PollStatus,
    @SerialName("is_anonymous") val isAnonymous: Boolean = true,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("opened_at") val openedAt: Instant? = null,
    @SerialName("closed_at") val closedAt: Instant? = null,
)

@Serializable
enum class PollStatus {
    @SerialName("draft") DRAFT,
    @SerialName("open") OPEN,
    @SerialName("closed") CLOSED,
    @SerialName("archived") ARCHIVED;

    val canVote: Boolean get() = this == OPEN
}

@Serializable
data class PollOption(
    val id: String,
    @SerialName("poll_id") val pollId: String,
    val text: String,
    val position: Int,
    @SerialName("created_at") val createdAt: Instant,
)

@Serializable
data class PollVote(
    val id: String,
    @SerialName("poll_id") val pollId: String,
    @SerialName("option_id") val optionId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("created_at") val createdAt: Instant,
)

@Serializable
data class PollDraft(
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("created_by") val createdBy: String? = null,
    val question: String,
    val status: PollStatus = PollStatus.OPEN,
    @SerialName("is_anonymous") val isAnonymous: Boolean = true,
)

@Serializable
data class PollOptionDraft(
    @SerialName("poll_id") val pollId: String,
    val text: String,
    val position: Int,
)

@Serializable
data class PollVoteDraft(
    @SerialName("poll_id") val pollId: String,
    @SerialName("option_id") val optionId: String,
    @SerialName("participant_id") val participantId: String,
)
