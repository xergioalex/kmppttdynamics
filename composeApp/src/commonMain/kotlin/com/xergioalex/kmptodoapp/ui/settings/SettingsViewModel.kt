package com.xergioalex.kmptodoapp.ui.settings

import androidx.lifecycle.ViewModel
import com.xergioalex.kmptodoapp.settings.AppSettings
import com.xergioalex.kmptodoapp.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val settings: AppSettings) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
}
