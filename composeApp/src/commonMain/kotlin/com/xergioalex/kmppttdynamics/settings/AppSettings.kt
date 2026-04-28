package com.xergioalex.kmppttdynamics.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppSettings(private val backing: Settings) {

    private val _themeMode = MutableStateFlow(load(KEY_THEME, ThemeMode.SYSTEM, ThemeMode::valueOf))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Last display name the participant used — pre-fills the join screen. */
    fun lastDisplayName(): String? = backing.getStringOrNull(KEY_DISPLAY_NAME)?.takeIf { it.isNotBlank() }

    fun setLastDisplayName(name: String) {
        backing.putString(KEY_DISPLAY_NAME, name)
    }

    fun setThemeMode(mode: ThemeMode) {
        backing.putString(KEY_THEME, mode.name)
        _themeMode.value = mode
    }

    private fun <T : Enum<T>> load(key: String, default: T, parse: (String) -> T): T =
        backing.getStringOrNull(key)?.let { runCatching { parse(it) }.getOrNull() } ?: default

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}
