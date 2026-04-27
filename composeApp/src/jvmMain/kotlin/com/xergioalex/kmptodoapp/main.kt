package com.xergioalex.kmptodoapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        // FORK-RENAME: window title is user-visible on Desktop. See docs/FORK_CUSTOMIZATION.md.
        title = "KMP Todo App",
    ) {
        App()
    }
}