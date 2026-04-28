package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A participant inside a meetup room. */
@Serializable
data class MeetupParticipant(
    val id: String,
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("display_name") val displayName: String,
    val role: ParticipantRole,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("joined_at") val joinedAt: Instant,
    @SerialName("last_seen_at") val lastSeenAt: Instant? = null,
)

@Serializable
enum class ParticipantRole {
    @SerialName("host") HOST,
    @SerialName("participant") PARTICIPANT,
    @SerialName("moderator") MODERATOR;
}

/** Inputs for inserting a new participant. */
@Serializable
data class JoinRequest(
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("display_name") val displayName: String,
    val role: ParticipantRole,
    @SerialName("is_online") val isOnline: Boolean = true,
)
