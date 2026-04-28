package com.xergioalex.kmppttdynamics

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.NSUserDefaultsSettings
import com.xergioalex.kmppttdynamics.settings.AppSettings
import platform.Foundation.NSUserDefaults

private val container by lazy {
    AppContainer(
        settings = AppSettings(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)),
    ).also { it.startGlobalPresence() }
}

@Suppress("FunctionName")
fun MainViewController() = ComposeUIViewController { App(container) }
