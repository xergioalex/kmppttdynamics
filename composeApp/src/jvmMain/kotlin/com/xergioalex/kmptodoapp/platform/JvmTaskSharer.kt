package com.xergioalex.kmptodoapp.platform

import com.xergioalex.kmptodoapp.domain.Task
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class JvmTaskSharer : TaskSharer {
    override fun share(task: Task) {
        val text = task.toShareText()
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }
}
