package com.xergioalex.kmptodoapp.domain

import kotlin.time.Instant

data class Task(
    val id: Long,
    val title: String,
    val notes: String,
    val category: String,
    val priority: Priority,
    val dueAt: Instant?,
    val isDone: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
