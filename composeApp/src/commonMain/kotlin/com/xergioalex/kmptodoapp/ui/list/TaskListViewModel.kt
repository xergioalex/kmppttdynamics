package com.xergioalex.kmptodoapp.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xergioalex.kmptodoapp.domain.Task
import com.xergioalex.kmptodoapp.domain.TaskFilter
import com.xergioalex.kmptodoapp.domain.TaskRepository
import com.xergioalex.kmptodoapp.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskListUiState(
    val items: List<Task> = emptyList(),
    val categories: List<String> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val query: String = "",
    val activeCount: Int = 0,
    val doneCount: Int = 0,
)

class TaskListViewModel(
    private val tasks: TaskRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<TaskListUiState> =
        combine(
            tasks.observeAll(),
            tasks.observeCategories(),
            settings.filter,
            query.asStateFlow(),
        ) { all, categories, filter, q ->
            val active = all.count { !it.isDone }
            val done = all.size - active
            val filtered = all
                .filter { task ->
                    when (filter) {
                        TaskFilter.ALL -> true
                        TaskFilter.ACTIVE -> !task.isDone
                        TaskFilter.DONE -> task.isDone
                    }
                }
                .filter { task ->
                    if (q.isBlank()) true
                    else listOf(task.title, task.notes, task.category).any { it.contains(q, ignoreCase = true) }
                }
            TaskListUiState(
                items = filtered,
                categories = categories,
                filter = filter,
                query = q,
                activeCount = active,
                doneCount = done,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState())

    fun setFilter(filter: TaskFilter) = settings.setFilter(filter)
    fun setQuery(text: String) { query.value = text }

    fun toggleDone(task: Task) {
        viewModelScope.launch { tasks.setDone(task.id, !task.isDone) }
    }

    fun delete(task: Task) {
        viewModelScope.launch { tasks.delete(task.id) }
    }

    fun clearCompleted() {
        viewModelScope.launch { tasks.deleteCompleted() }
    }
}
