package com.xergioalex.kmppttdynamics.domain

import kotlin.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The cross-meetup identity for a single install. Every device picks
 * one of these on first launch and reuses it everywhere — display
 * name + avatar follow the user into every meetup they join, replacing
 * the per-meetup `display_name` prompt the app used to show.
 *
 * @property clientId The install-stable id (also used as `client_id` on
 *                    `meetup_participants`).
 * @property avatarId 1-based index into the bundled avatar set under
 *                    `composeResources/files/avatars/<avatarId>.png`.
 *                    Globally unique — the database enforces "one
 *                    avatar per user" so the picker can render taken
 *                    avatars as locked.
 */
@Serializable
data class AppUser(
    @SerialName("client_id") val clientId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_id") val avatarId: Int,
    @SerialName("created_at") val createdAt: Instant? = null,
    @SerialName("updated_at") val updatedAt: Instant? = null,
)

/**
 * Insert / upsert payload for [AppUser]. We carry @EncodeDefault on
 * the integer fields so kotlinx.serialization always sends them — the
 * same rule we hit on JoinRequest where dropped fields silently
 * defaulted to NULL on the server side.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AppUserDraft(
    @SerialName("client_id") val clientId: String,
    @SerialName("display_name") val displayName: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("avatar_id") val avatarId: Int,
)
