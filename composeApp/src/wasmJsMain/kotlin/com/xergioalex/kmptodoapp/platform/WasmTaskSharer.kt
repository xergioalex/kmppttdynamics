package com.xergioalex.kmptodoapp.platform

import com.xergioalex.kmptodoapp.domain.Task
import kotlinx.browser.window

class WasmTaskSharer : TaskSharer {
    override fun share(task: Task) {
        val text = task.toShareText()
        runCatching { window.navigator.clipboard.writeText(text) }
    }
}
