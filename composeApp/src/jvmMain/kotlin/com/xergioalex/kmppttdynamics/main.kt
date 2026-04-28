package com.xergioalex.kmppttdynamics

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.PreferencesSettings
import com.xergioalex.kmppttdynamics.settings.AppSettings
import java.util.prefs.Preferences

fun main() {
    val container = AppContainer(
        settings = AppSettings(
            PreferencesSettings(Preferences.userRoot().node("com/xergioalex/kmppttdynamics")),
        ),
    )
    application {
        val state = rememberWindowState(size = DpSize(1100.dp, 720.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "PTT Dynamics",
        ) {
            App(container)
        }
    }
}
