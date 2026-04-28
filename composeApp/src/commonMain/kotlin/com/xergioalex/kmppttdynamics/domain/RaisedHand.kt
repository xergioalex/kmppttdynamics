package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One `raised_hands` row. The unique partial index in the SQL ensures
 * a participant has at most one ACTIVE hand (raised | acknowledged |
 * speaking) per meetup.
 */
@Serializable
data class RaisedHand(
    val id: String,
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("participant_id") val participantId: String,
    val message: String? = null,
    val status: HandStatus,
    @SerialName("raised_at") val raisedAt: Instant,
    @SerialName("acknowledged_at") val acknowledgedAt: Instant? = null,
    @SerialName("lowered_at") val loweredAt: Instant? = null,
)

@Serializable
enum class HandStatus {
    @SerialName("raised") RAISED,
    @SerialName("acknowledged") ACKNOWLEDGED,
    @SerialName("speaking") SPEAKING,
    @SerialName("lowered") LOWERED,
    @SerialName("dismissed") DISMISSED;

    val isActive: Boolean
        get() = this == RAISED || this == ACKNOWLEDGED || this == SPEAKING
}

@Serializable
data class RaiseHandDraft(
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("participant_id") val participantId: String,
    val message: String? = null,
)
