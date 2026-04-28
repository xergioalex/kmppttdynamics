package com.xergioalex.kmppttdynamics

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.russhwolf.settings.StorageSettings
import com.xergioalex.kmppttdynamics.settings.AppSettings

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = AppContainer(
        settings = AppSettings(StorageSettings()),
    )
    ComposeViewport {
        App(container)
    }
}
