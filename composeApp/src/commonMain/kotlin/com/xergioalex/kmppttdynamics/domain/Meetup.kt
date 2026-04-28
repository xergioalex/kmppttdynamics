package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A meetup room. Mirrors the `meetups` Postgres table. */
@Serializable
data class Meetup(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("join_code") val joinCode: String,
    val status: MeetupStatus,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("starts_at") val startsAt: Instant? = null,
    @SerialName("ended_at") val endedAt: Instant? = null,
    @SerialName("created_at") val createdAt: Instant,
)

@Serializable
enum class MeetupStatus {
    @SerialName("draft") DRAFT,
    @SerialName("live") LIVE,
    @SerialName("paused") PAUSED,
    @SerialName("ended") ENDED,
    @SerialName("archived") ARCHIVED;

    val isLiveSession: Boolean
        get() = this == LIVE || this == PAUSED
}

/** Inputs for creating a new meetup. The id and timestamps come from Postgres. */
@Serializable
data class MeetupDraft(
    val title: String,
    val description: String? = null,
    @SerialName("join_code") val joinCode: String,
    val status: MeetupStatus = MeetupStatus.DRAFT,
)
