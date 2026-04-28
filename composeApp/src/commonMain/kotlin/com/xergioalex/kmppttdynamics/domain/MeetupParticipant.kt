package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A participant inside a meetup room. */
@Serializable
data class MeetupParticipant(
    val id: String,
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("client_id") val clientId: String? = null,
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

/**
 * Inputs for inserting a new participant. The [isOnline] field carries
 * @EncodeDefault so the JSON sent to Postgres always includes
 * `"is_online": true`. Without it, kotlinx.serialization's default
 * `encodeDefaults = false` policy would drop the field and the Postgres
 * column default (`false`) would win — which is the original "0 online"
 * bug we hit on first room entry.
 *
 * [clientId] is the install-stable identifier for this device. The
 * `(meetup_id, client_id)` unique partial index in the database gives
 * us hard idempotency: re-joining the same meetup from the same device
 * always upserts onto the same row instead of creating a duplicate.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class JoinRequest(
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("display_name") val displayName: String,
    val role: ParticipantRole,
    @SerialName("client_id") val clientId: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("is_online") val isOnline: Boolean = true,
)
