package com.xergioalex.kmptodoapp.settings

import com.russhwolf.settings.Settings
import com.xergioalex.kmptodoapp.domain.TaskFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class AppSettings(private val backing: Settings) {

    private val _themeMode = MutableStateFlow(load(KEY_THEME, ThemeMode.SYSTEM, ThemeMode::valueOf))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _filter = MutableStateFlow(load(KEY_FILTER, TaskFilter.ALL, TaskFilter::valueOf))
    val filter: StateFlow<TaskFilter> = _filter.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        backing.putString(KEY_THEME, mode.name)
        _themeMode.value = mode
    }

    fun setFilter(filter: TaskFilter) {
        backing.putString(KEY_FILTER, filter.name)
        _filter.value = filter
    }

    private fun <T : Enum<T>> load(key: String, default: T, parse: (String) -> T): T =
        backing.getStringOrNull(key)?.let { runCatching { parse(it) }.getOrNull() } ?: default

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_FILTER = "task_filter"
    }
}
