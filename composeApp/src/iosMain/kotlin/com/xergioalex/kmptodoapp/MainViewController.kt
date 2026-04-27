package com.xergioalex.kmptodoapp

import androidx.compose.ui.window.ComposeUIViewController
import com.russhwolf.settings.NSUserDefaultsSettings
import com.xergioalex.kmptodoapp.data.DatabaseDriverFactory
import com.xergioalex.kmptodoapp.data.SqlTaskRepository
import com.xergioalex.kmptodoapp.platform.IosTaskSharer
import com.xergioalex.kmptodoapp.settings.AppSettings
import platform.Foundation.NSUserDefaults

private val container by lazy {
    AppContainer(
        tasks = SqlTaskRepository(DatabaseDriverFactory()),
        settings = AppSettings(NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)),
        sharer = IosTaskSharer(),
    )
}

fun MainViewController() = ComposeUIViewController { App(container) }
