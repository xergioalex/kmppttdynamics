package com.xergioalex.kmptodoapp.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xergioalex.kmptodoapp.AppContainer
import com.xergioalex.kmptodoapp.domain.Priority
import com.xergioalex.kmptodoapp.ui.formatDueDate
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.action_back
import kmptodoapp.composeapp.generated.resources.action_cancel
import kmptodoapp.composeapp.generated.resources.action_delete
import kmptodoapp.composeapp.generated.resources.action_save
import kmptodoapp.composeapp.generated.resources.action_share
import kmptodoapp.composeapp.generated.resources.field_category
import kmptodoapp.composeapp.generated.resources.field_done
import kmptodoapp.composeapp.generated.resources.field_due_at
import kmptodoapp.composeapp.generated.resources.field_no_due
import kmptodoapp.composeapp.generated.resources.field_notes
import kmptodoapp.composeapp.generated.resources.field_priority
import kmptodoapp.composeapp.generated.resources.field_title
import kmptodoapp.composeapp.generated.resources.priority_high
import kmptodoapp.composeapp.generated.resources.priority_low
import kmptodoapp.composeapp.generated.resources.priority_medium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    container: AppContainer,
    taskId: Long?,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel: TaskEditViewModel = viewModel(key = "edit-${taskId ?: "new"}") {
        TaskEditViewModel(container.tasks, taskId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == null) "Nuevo" else "Detalle") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                        }
                    }
                },
                actions = {
                    if (state.id != null) {
                        IconButton(onClick = {
                            container.tasks // touch to satisfy reference; share uses sharer
                            val now = Clock.System.now()
                            container.sharer.share(
                                com.xergioalex.kmptodoapp.domain.Task(
                                    id = state.id ?: 0L,
                                    title = state.title,
                                    notes = state.notes,
                                    category = state.category,
                                    priority = state.priority,
                                    dueAt = state.dueAt,
                                    isDone = state.isDone,
                                    createdAt = now,
                                    updatedAt = now,
                                )
                            )
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.action_share))
                        }
                        IconButton(onClick = { viewModel.delete() }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    }
                    IconButton(onClick = { viewModel.save() }, enabled = state.canSave) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(Res.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text(stringResource(Res.string.field_title)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(Res.string.field_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedTextField(
                value = state.category,
                onValueChange = viewModel::setCategory,
                label = { Text(stringResource(Res.string.field_category)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                text = stringResource(Res.string.field_priority),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { p ->
                    val label = when (p) {
                        Priority.HIGH -> stringResource(Res.string.priority_high)
                        Priority.MEDIUM -> stringResource(Res.string.priority_medium)
                        Priority.LOW -> stringResource(Res.string.priority_low)
                    }
                    FilterChip(
                        selected = p == state.priority,
                        onClick = { viewModel.setPriority(p) },
                        label = { Text(label) },
                    )
                }
            }
            DueDatePicker(
                current = state.dueAt,
                onChange = viewModel::setDueAt,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.field_done),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.isDone, onCheckedChange = viewModel::setDone)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePicker(current: Instant?, onChange: (Instant?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = current?.toEpochMilliseconds() ?: Clock.System.now().toEpochMilliseconds(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.field_due_at),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (current != null) {
            AssistChip(
                onClick = { onChange(null) },
                label = { Text(formatDueDate(current)) },
                trailingIcon = { Icon(Icons.Filled.Clear, contentDescription = null) },
            )
        } else {
            OutlinedButton(onClick = { showPicker = true }) {
                Text(stringResource(Res.string.field_no_due))
            }
        }
    }
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(Instant.fromEpochMilliseconds(it)) }
                    showPicker = false
                }) { Text(stringResource(Res.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
