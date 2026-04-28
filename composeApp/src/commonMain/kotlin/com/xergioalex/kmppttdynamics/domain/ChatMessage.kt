package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row in the `chat_messages` table. */
@Serializable
data class ChatMessage(
    val id: String,
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("participant_id") val participantId: String? = null,
    val message: String,
    val type: ChatType,
    val status: ChatStatus,
    @SerialName("created_at") val createdAt: Instant,
)

@Serializable
enum class ChatType {
    @SerialName("message") MESSAGE,
    @SerialName("announcement") ANNOUNCEMENT,
    @SerialName("system") SYSTEM,
}

@Serializable
enum class ChatStatus {
    @SerialName("visible") VISIBLE,
    @SerialName("hidden") HIDDEN,
    @SerialName("deleted") DELETED,
}

/** Insert payload — the DB defaults `status` to `visible` and `type` to `message`. */
@Serializable
data class ChatDraft(
    @SerialName("meetup_id") val meetupId: String,
    @SerialName("participant_id") val participantId: String? = null,
    val message: String,
    val type: ChatType = ChatType.MESSAGE,
)
