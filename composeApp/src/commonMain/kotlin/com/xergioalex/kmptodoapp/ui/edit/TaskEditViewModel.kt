package com.xergioalex.kmptodoapp.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmptodoapp.domain.Priority
import com.xergioalex.kmptodoapp.domain.TaskDraft
import com.xergioalex.kmptodoapp.domain.TaskRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Instant

data class TaskEditUiState(
    val id: Long? = null,
    val title: String = "",
    val notes: String = "",
    val category: String = "",
    val priority: Priority = Priority.MEDIUM,
    val dueAt: Instant? = null,
    val isDone: Boolean = false,
    val loading: Boolean = false,
) {
    val canSave: Boolean get() = title.trim().isNotEmpty()
}

sealed interface TaskEditEffect {
    data object Saved : TaskEditEffect
    data object Deleted : TaskEditEffect
    data object NotFound : TaskEditEffect
}

class TaskEditViewModel(
    private val tasks: TaskRepository,
    private val taskId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskEditUiState(id = taskId, loading = taskId != null))
    val state: StateFlow<TaskEditUiState> = _state.asStateFlow()

    // One-shot navigation events. Channel (not StateFlow) so a cached VM doesn't
    // replay "Saved" the next time the screen attaches to it — that bug bit us
    // on the "Add task" flow when the same VM key (edit-new) was reused.
    private val _effects = Channel<TaskEditEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val task = tasks.getById(taskId)
                if (task != null) {
                    _state.value = TaskEditUiState(
                        id = task.id,
                        title = task.title,
                        notes = task.notes,
                        category = task.category,
                        priority = task.priority,
                        dueAt = task.dueAt,
                        isDone = task.isDone,
                    )
                } else {
                    _state.value = _state.value.copy(loading = false)
                    _effects.send(TaskEditEffect.NotFound)
                }
            }
        }
    }

    fun setTitle(value: String) { _state.value = _state.value.copy(title = value) }
    fun setNotes(value: String) { _state.value = _state.value.copy(notes = value) }
    fun setCategory(value: String) { _state.value = _state.value.copy(category = value) }
    fun setPriority(value: Priority) { _state.value = _state.value.copy(priority = value) }
    fun setDueAt(value: Instant?) { _state.value = _state.value.copy(dueAt = value) }
    fun setDone(value: Boolean) { _state.value = _state.value.copy(isDone = value) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val draft = TaskDraft(
            title = s.title.trim(),
            notes = s.notes.trim(),
            category = s.category.trim(),
            priority = s.priority,
            dueAt = s.dueAt,
            isDone = s.isDone,
        )
        viewModelScope.launch {
            if (s.id == null) tasks.create(draft) else tasks.update(s.id, draft)
            // Reset transient form state so the cached VM is reusable for a
            // brand-new task the next time the user enters this screen.
            _state.value = TaskEditUiState(id = null)
            _effects.send(TaskEditEffect.Saved)
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            tasks.delete(id)
            _effects.send(TaskEditEffect.Deleted)
        }
    }
}
