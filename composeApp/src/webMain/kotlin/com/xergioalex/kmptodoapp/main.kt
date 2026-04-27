package com.xergioalex.kmptodoapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.russhwolf.settings.StorageSettings
import com.xergioalex.kmptodoapp.data.InMemoryTaskRepository
import com.xergioalex.kmptodoapp.platform.TaskSharer
import com.xergioalex.kmptodoapp.settings.AppSettings

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = AppContainer(
        tasks = InMemoryTaskRepository(),
        settings = AppSettings(StorageSettings()),
        sharer = createTaskSharer(),
    )
    ComposeViewport {
        App(container)
    }
}

internal expect fun createTaskSharer(): TaskSharer
