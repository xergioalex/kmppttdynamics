package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Raffle(
    val id: String,
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("created_by") val createdBy: String? = null,
    val title: String,
    val status: RaffleStatus,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("opened_at") val openedAt: Instant? = null,
    @SerialName("drawn_at") val drawnAt: Instant? = null,
    @SerialName("closed_at") val closedAt: Instant? = null,
)

@Serializable
enum class RaffleStatus {
    @SerialName("draft") DRAFT,
    @SerialName("open") OPEN,
    @SerialName("drawn") DRAWN,
    @SerialName("closed") CLOSED,
    @SerialName("archived") ARCHIVED;

    val acceptsEntries: Boolean get() = this == OPEN
}

@Serializable
data class RaffleEntry(
    val id: String,
    @SerialName("raffle_id") val raffleId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("created_at") val createdAt: Instant,
)

@Serializable
data class RaffleWinner(
    val id: String,
    @SerialName("raffle_id") val raffleId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("drawn_at") val drawnAt: Instant,
)

/**
 * Insert payload for a new raffle. See [PollDraft] for why [status] is
 * tagged with @EncodeDefault.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RaffleDraft(
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("created_by") val createdBy: String? = null,
    val title: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val status: RaffleStatus = RaffleStatus.OPEN,
)

@Serializable
data class RaffleEntryDraft(
    @SerialName("raffle_id") val raffleId: String,
    @SerialName("participant_id") val participantId: String,
)

@Serializable
data class RaffleWinnerDraft(
    @SerialName("raffle_id") val raffleId: String,
    @SerialName("participant_id") val participantId: String,
)
