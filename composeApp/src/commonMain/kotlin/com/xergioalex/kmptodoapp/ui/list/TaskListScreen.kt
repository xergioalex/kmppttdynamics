package com.xergioalex.kmptodoapp.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xergioalex.kmptodoapp.AppContainer
import com.xergioalex.kmptodoapp.domain.Priority
import com.xergioalex.kmptodoapp.domain.Task
import com.xergioalex.kmptodoapp.domain.TaskFilter
import com.xergioalex.kmptodoapp.ui.formatDueDate
import org.jetbrains.compose.resources.stringResource
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.action_add
import kmptodoapp.composeapp.generated.resources.action_clear_completed
import kmptodoapp.composeapp.generated.resources.action_delete
import kmptodoapp.composeapp.generated.resources.action_settings
import kmptodoapp.composeapp.generated.resources.action_share
import kmptodoapp.composeapp.generated.resources.app_title
import kmptodoapp.composeapp.generated.resources.empty_no_results
import kmptodoapp.composeapp.generated.resources.empty_no_tasks
import kmptodoapp.composeapp.generated.resources.filter_active
import kmptodoapp.composeapp.generated.resources.filter_all
import kmptodoapp.composeapp.generated.resources.filter_done
import kmptodoapp.composeapp.generated.resources.priority_high
import kmptodoapp.composeapp.generated.resources.priority_low
import kmptodoapp.composeapp.generated.resources.priority_medium
import kmptodoapp.composeapp.generated.resources.search_placeholder
import kmptodoapp.composeapp.generated.resources.task_count_active

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    container: AppContainer,
    selectedTaskId: Long?,
    onCreateTask: () -> Unit,
    onSelectTask: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: TaskListViewModel = viewModel { TaskListViewModel(container.tasks, container.settings) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_title)) },
                actions = {
                    IconButton(onClick = { viewModel.clearCompleted() }) {
                        Icon(IconGlyph.Sweep, contentDescription = stringResource(Res.string.action_clear_completed))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(IconGlyph.Settings, contentDescription = stringResource(Res.string.action_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTask) {
                Icon(IconGlyph.Add, contentDescription = stringResource(Res.string.action_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                singleLine = true,
            )
            FilterRow(
                selected = state.filter,
                onSelect = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Text(
                text = stringResource(Res.string.task_count_active, state.activeCount, state.doneCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            HorizontalDivider()
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.items.isEmpty()) {
                    EmptyState(
                        message = stringResource(
                            if (state.activeCount + state.doneCount == 0)
                                Res.string.empty_no_tasks
                            else
                                Res.string.empty_no_results
                        ),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.items, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                isSelected = task.id == selectedTaskId,
                                onClick = { onSelectTask(task.id) },
                                onToggle = { viewModel.toggleDone(task) },
                                onDelete = { viewModel.delete(task) },
                                onShare = { container.sharer.share(task) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: TaskFilter,
    onSelect: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskFilter.entries.forEach { filter ->
            val label = when (filter) {
                TaskFilter.ALL -> stringResource(Res.string.filter_all)
                TaskFilter.ACTIVE -> stringResource(Res.string.filter_active)
                TaskFilter.DONE -> stringResource(Res.string.filter_done)
            }
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val border = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, border) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityBadge(task.priority)
                    if (task.category.isNotEmpty()) {
                        CategoryChip(task.category, modifier = Modifier.padding(start = 6.dp))
                    }
                    task.dueAt?.let { due ->
                        Text(
                            text = formatDueDate(due),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            IconButton(onClick = onShare) {
                Icon(IconGlyph.Share, contentDescription = stringResource(Res.string.action_share))
            }
            IconButton(onClick = onDelete) {
                Icon(IconGlyph.Delete, contentDescription = stringResource(Res.string.action_delete))
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: Priority) {
    val (label, container, content) = when (priority) {
        Priority.HIGH -> Triple(
            stringResource(Res.string.priority_high),
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onPrimary,
        )
        Priority.MEDIUM -> Triple(
            stringResource(Res.string.priority_medium),
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary,
        )
        Priority.LOW -> Triple(
            stringResource(Res.string.priority_low),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .padding(end = 4.dp)
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun CategoryChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = "#$text",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = IconGlyph.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

