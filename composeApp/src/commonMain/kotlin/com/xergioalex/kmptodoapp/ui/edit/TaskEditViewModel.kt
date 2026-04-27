package com.xergioalex.kmptodoapp.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmptodoapp.domain.Priority
import com.xergioalex.kmptodoapp.domain.TaskDraft
import com.xergioalex.kmptodoapp.domain.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val saved: Boolean = false,
    val deleted: Boolean = false,
) {
    val canSave: Boolean get() = title.trim().isNotEmpty()
}

class TaskEditViewModel(
    private val tasks: TaskRepository,
    private val taskId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskEditUiState(id = taskId, loading = taskId != null))
    val state: StateFlow<TaskEditUiState> = _state.asStateFlow()

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
                    _state.value = _state.value.copy(loading = false, deleted = true)
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
            _state.value = s.copy(saved = true)
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            tasks.delete(id)
            _state.value = _state.value.copy(deleted = true)
        }
    }
}
