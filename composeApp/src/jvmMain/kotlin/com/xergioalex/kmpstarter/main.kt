package com.xergioalex.kmpstarter

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        // FORK-RENAME: window title is user-visible on Desktop. See docs/FORK_CUSTOMIZATION.md.
        title = "KMP Starter",
    ) {
        App()
    }
}