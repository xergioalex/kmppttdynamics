package com.xergioalex.kmptodoapp

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.PreferencesSettings
import com.xergioalex.kmptodoapp.data.DatabaseDriverFactory
import com.xergioalex.kmptodoapp.data.SqlTaskRepository
import com.xergioalex.kmptodoapp.platform.JvmTaskSharer
import com.xergioalex.kmptodoapp.settings.AppSettings
import java.util.prefs.Preferences

fun main() {
    val container = AppContainer(
        tasks = SqlTaskRepository(DatabaseDriverFactory()),
        settings = AppSettings(PreferencesSettings(Preferences.userRoot().node("com/xergioalex/kmptodoapp"))),
        sharer = JvmTaskSharer(),
    )
    application {
        val state = rememberWindowState(size = DpSize(1100.dp, 720.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            // FORK-RENAME: window title is user-visible on Desktop. See docs/FORK_CUSTOMIZATION.md.
            title = "KMP Todo App",
        ) {
            App(container)
        }
    }
}
