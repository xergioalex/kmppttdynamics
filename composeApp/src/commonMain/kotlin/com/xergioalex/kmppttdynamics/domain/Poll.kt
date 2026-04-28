package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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

/**
 * Insert payload for a new poll.
 *
 * Fields like [status] and [isAnonymous] carry @EncodeDefault so the
 * value is always sent over the wire even when it equals its Kotlin
 * default. Without this annotation, kotlinx.serialization's default
 * `encodeDefaults = false` policy would silently drop them and Postgres
 * would fall back to the column default ('draft' / 'true'), which is
 * the bug that left every freshly created poll stuck on Draft and
 * therefore unvotable.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PollDraft(
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("created_by") val createdBy: String? = null,
    val question: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val status: PollStatus = PollStatus.OPEN,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
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
