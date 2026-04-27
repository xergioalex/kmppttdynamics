package com.xergioalex.kmptodoapp.domain

import kotlin.time.Instant

data class TaskDraft(
    val title: String,
    val notes: String = "",
    val category: String = "",
    val priority: Priority = Priority.MEDIUM,
    val dueAt: Instant? = null,
    val isDone: Boolean = false,
)
