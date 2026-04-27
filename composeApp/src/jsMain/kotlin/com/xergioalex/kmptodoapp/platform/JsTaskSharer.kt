package com.xergioalex.kmptodoapp.platform

import com.xergioalex.kmptodoapp.domain.Task
import kotlinx.browser.window

class JsTaskSharer : TaskSharer {
    override fun share(task: Task) {
        val text = task.toShareText()
        // Best-effort: copy to clipboard (Web Share API is gated by user activation
        // and not always available; clipboard works everywhere we care about).
        runCatching { window.navigator.clipboard.writeText(text) }
    }
}
