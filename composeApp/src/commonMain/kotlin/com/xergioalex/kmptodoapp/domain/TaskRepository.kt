package com.xergioalex.kmptodoapp.domain

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeAll(): Flow<List<Task>>
    fun observeCategories(): Flow<List<String>>
    suspend fun getById(id: Long): Task?
    suspend fun create(draft: TaskDraft): Long
    suspend fun update(id: Long, draft: TaskDraft)
    suspend fun setDone(id: Long, done: Boolean)
    suspend fun delete(id: Long)
    suspend fun deleteCompleted()
}
