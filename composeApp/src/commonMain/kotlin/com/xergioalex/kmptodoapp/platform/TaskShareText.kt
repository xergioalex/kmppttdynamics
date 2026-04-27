package com.xergioalex.kmptodoapp.platform

import com.xergioalex.kmptodoapp.domain.Task
import com.xergioalex.kmptodoapp.ui.formatDueDate

internal fun Task.toShareText(): String = buildString {
    append("• ")
    append(title)
    if (notes.isNotBlank()) {
        append("\n\n")
        append(notes)
    }
    if (category.isNotBlank()) {
        append("\n#")
        append(category)
    }
    dueAt?.let {
        append("\n⏰ ")
        append(formatDueDate(it))
    }
}
