package com.xergioalex.kmppttdynamics.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Local profile snapshot. `null` until onboarding completes. */
data class LocalProfile(val displayName: String, val avatarId: Int)

/**
 * App-wide persistent settings backed by `multiplatform-settings`.
 *
 * Persists:
 * - **theme** mode
 * - **install client id** (stable random string used as the presence
 *   key on the realtime lobby and the `client_id` on every meetup
 *   participant row — primary defense against duplicate rows after
 *   reconnects, hot reloads, and reinstalls of the same install)
 * - **profile** (display name + avatar id) chosen during onboarding
 *   and reused across every meetup the device joins
 * - **per-meetup map** of `meetupId -> participantId` so we can
 *   re-enter rooms without re-creating participant rows
 *
 * The profile is exposed as a `StateFlow<LocalProfile?>` so the UI can
 * gate the initial routing on whether the user has finished onboarding.
 */
class AppSettings(private val backing: Settings) {

    private val _themeMode = MutableStateFlow(load(KEY_THEME, ThemeMode.SYSTEM, ThemeMode::valueOf))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<LocalProfile?> = _profile.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        backing.putString(KEY_THEME, mode.name)
        _themeMode.value = mode
    }

    fun setProfile(displayName: String, avatarId: Int) {
        backing.putString(KEY_DISPLAY_NAME, displayName)
        backing.putInt(KEY_AVATAR_ID, avatarId)
        _profile.value = LocalProfile(displayName, avatarId)
    }

    /** Returns the participant ID this device has previously used for [meetupId], or null. */
    fun participantIdFor(meetupId: String): String? =
        backing.getStringOrNull(participantKey(meetupId))?.takeIf { it.isNotBlank() }

    fun setParticipantIdFor(meetupId: String, participantId: String) {
        backing.putString(participantKey(meetupId), participantId)
    }

    fun clearParticipantIdFor(meetupId: String) {
        backing.remove(participantKey(meetupId))
    }

    /**
     * Stable client identifier for this app install. Generated once and
     * persisted; reused for every presence track call so reconnecting
     * doesn't leave ghosts in the global lobby count, and as the
     * `client_id` on `meetup_participants` rows.
     */
    fun installClientId(): String {
        backing.getStringOrNull(KEY_INSTALL_CLIENT_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = (1..16).map { kotlin.random.Random.nextInt(0, 16).toString(16) }.joinToString("")
        backing.putString(KEY_INSTALL_CLIENT_ID, id)
        return id
    }

    private fun loadProfile(): LocalProfile? {
        val name = backing.getStringOrNull(KEY_DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: return null
        val avatarId = backing.getIntOrNull(KEY_AVATAR_ID) ?: return null
        return LocalProfile(name, avatarId)
    }

    private fun participantKey(meetupId: String): String = "${KEY_PARTICIPANT_PREFIX}$meetupId"

    private fun <T : Enum<T>> load(key: String, default: T, parse: (String) -> T): T =
        backing.getStringOrNull(key)?.let { runCatching { parse(it) }.getOrNull() } ?: default

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_AVATAR_ID = "avatar_id"
        const val KEY_PARTICIPANT_PREFIX = "participant_id_for_meetup__"
        const val KEY_INSTALL_CLIENT_ID = "install_client_id"
    }
}
