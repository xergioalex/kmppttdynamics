package com.xergioalex.kmptodoapp.data

import com.xergioalex.kmptodoapp.domain.Task
import com.xergioalex.kmptodoapp.domain.TaskDraft
import com.xergioalex.kmptodoapp.domain.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

// FIXME(web-persistence): swap for SQLDelight web-worker-driver once webpack/sqljs setup
// lands. Today JS/Wasm builds reset state on reload, while Android/iOS/Desktop persist
// to a real SQLite file via SqlTaskRepository.
class InMemoryTaskRepository(
    private val clock: Clock = Clock.System,
) : TaskRepository {

    private val state = MutableStateFlow<List<Task>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Task>> = state.asStateFlow().map { list -> list.sorted() }

    override fun observeCategories(): Flow<List<String>> =
        state.asStateFlow().map { list ->
            list.asSequence().map { it.category }.filter { it.isNotEmpty() }.distinct().sorted().toList()
        }

    override suspend fun getById(id: Long): Task? = state.value.firstOrNull { it.id == id }

    override suspend fun create(draft: TaskDraft): Long {
        val now = clock.now()
        val id = nextId++
        val task = Task(
            id = id,
            title = draft.title,
            notes = draft.notes,
            category = draft.category,
            priority = draft.priority,
            dueAt = draft.dueAt,
            isDone = draft.isDone,
            createdAt = now,
            updatedAt = now,
        )
        state.update { it + task }
        return id
    }

    override suspend fun update(id: Long, draft: TaskDraft) {
        val now = clock.now()
        state.update { list ->
            list.map { existing ->
                if (existing.id != id) existing
                else existing.copy(
                    title = draft.title,
                    notes = draft.notes,
                    category = draft.category,
                    priority = draft.priority,
                    dueAt = draft.dueAt,
                    isDone = draft.isDone,
                    updatedAt = now,
                )
            }
        }
    }

    override suspend fun setDone(id: Long, done: Boolean) {
        val now = clock.now()
        state.update { list ->
            list.map { existing ->
                if (existing.id == id) existing.copy(isDone = done, updatedAt = now) else existing
            }
        }
    }

    override suspend fun delete(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun deleteCompleted() {
        state.update { list -> list.filterNot { it.isDone } }
    }

    private fun List<Task>.sorted(): List<Task> = sortedWith(
        compareBy<Task> { it.isDone }
            .thenByDescending { it.priority.storedValue }
            .thenBy { it.dueAt?.toEpochMilliseconds() ?: Long.MAX_VALUE }
            .thenByDescending { it.createdAt.toEpochMilliseconds() }
    )
}
