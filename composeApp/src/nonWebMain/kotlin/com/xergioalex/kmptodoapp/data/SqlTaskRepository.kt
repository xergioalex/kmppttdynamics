package com.xergioalex.kmptodoapp.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.xergioalex.kmptodoapp.db.TodoDatabase
import com.xergioalex.kmptodoapp.domain.Priority
import com.xergioalex.kmptodoapp.domain.Task
import com.xergioalex.kmptodoapp.domain.TaskDraft
import com.xergioalex.kmptodoapp.domain.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import com.xergioalex.kmptodoapp.db.Task as TaskRow

class SqlTaskRepository(
    factory: DatabaseDriverFactory,
    private val clock: Clock = Clock.System,
) : TaskRepository {

    private val database: TodoDatabase = TodoDatabase(factory.createDriver())
    private val queries = database.tasksQueries

    override fun observeAll(): Flow<List<Task>> =
        queries.selectAll().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeCategories(): Flow<List<String>> =
        queries.distinctCategories().asFlow().mapToList(Dispatchers.Default)

    override suspend fun getById(id: Long): Task? = withContext(Dispatchers.Default) {
        queries.selectById(id).asFlow().mapToOneOrNull(Dispatchers.Default).first()?.toDomain()
    }

    override suspend fun create(draft: TaskDraft): Long = withContext(Dispatchers.Default) {
        val now = clock.now().toEpochMilliseconds()
        queries.insert(
            title = draft.title,
            notes = draft.notes,
            category = draft.category,
            priority = draft.priority.storedValue.toLong(),
            due_at_epoch_ms = draft.dueAt?.toEpochMilliseconds(),
            is_done = if (draft.isDone) 1 else 0,
            created_at_epoch_ms = now,
            updated_at_epoch_ms = now,
        )
        queries.lastInsertedId().executeAsOne()
    }

    override suspend fun update(id: Long, draft: TaskDraft) {
        withContext(Dispatchers.Default) {
            val now = clock.now().toEpochMilliseconds()
            queries.update(
                title = draft.title,
                notes = draft.notes,
                category = draft.category,
                priority = draft.priority.storedValue.toLong(),
                due_at_epoch_ms = draft.dueAt?.toEpochMilliseconds(),
                is_done = if (draft.isDone) 1 else 0,
                updated_at_epoch_ms = now,
                id = id,
            )
        }
    }

    override suspend fun setDone(id: Long, done: Boolean) {
        withContext(Dispatchers.Default) {
            val now = clock.now().toEpochMilliseconds()
            queries.setDone(is_done = if (done) 1 else 0, updated_at_epoch_ms = now, id = id)
        }
    }

    override suspend fun delete(id: Long) {
        withContext(Dispatchers.Default) { queries.deleteById(id) }
    }

    override suspend fun deleteCompleted() {
        withContext(Dispatchers.Default) { queries.deleteCompleted() }
    }

    private fun TaskRow.toDomain(): Task = Task(
        id = id,
        title = title,
        notes = notes,
        category = category,
        priority = Priority.fromStoredValue(priority.toInt()),
        dueAt = due_at_epoch_ms?.let(Instant::fromEpochMilliseconds),
        isDone = is_done == 1L,
        createdAt = Instant.fromEpochMilliseconds(created_at_epoch_ms),
        updatedAt = Instant.fromEpochMilliseconds(updated_at_epoch_ms),
    )
}
