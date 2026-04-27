package com.xergioalex.kmptodoapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xergioalex.kmptodoapp.ui.edit.TaskEditScreen
import com.xergioalex.kmptodoapp.ui.list.TaskListScreen
import com.xergioalex.kmptodoapp.ui.settings.SettingsScreen
import com.xergioalex.kmptodoapp.ui.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.empty_detail

private sealed interface Destination {
    data object List : Destination
    data class Edit(val taskId: Long?) : Destination
    data object Settings : Destination
}

@Composable
fun App(container: AppContainer) {
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()

    AppTheme(themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize().safeContentPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            var destination by remember { mutableStateOf<Destination>(Destination.List) }
            var selectedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isWide = maxWidth >= 720.dp

                if (isWide) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().weight(1f)) {
                            TaskListScreen(
                                container = container,
                                selectedTaskId = selectedTaskId,
                                onCreateTask = {
                                    selectedTaskId = null
                                    destination = Destination.Edit(null)
                                },
                                onSelectTask = { id ->
                                    selectedTaskId = id
                                    destination = Destination.Edit(id)
                                },
                                onOpenSettings = { destination = Destination.Settings },
                            )
                        }
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Box(Modifier.fillMaxSize().weight(1.2f)) {
                            when (val d = destination) {
                                is Destination.Edit -> TaskEditScreen(
                                    container = container,
                                    taskId = d.taskId,
                                    showBackButton = false,
                                    onBack = { destination = Destination.List },
                                    onDone = { destination = Destination.List },
                                )
                                Destination.Settings -> SettingsScreen(
                                    container = container,
                                    showBackButton = true,
                                    onBack = { destination = Destination.List },
                                )
                                Destination.List -> EmptyDetail()
                            }
                        }
                    }
                } else {
                    when (val d = destination) {
                        Destination.List -> TaskListScreen(
                            container = container,
                            selectedTaskId = null,
                            onCreateTask = {
                                selectedTaskId = null
                                destination = Destination.Edit(null)
                            },
                            onSelectTask = { id ->
                                selectedTaskId = id
                                destination = Destination.Edit(id)
                            },
                            onOpenSettings = { destination = Destination.Settings },
                        )
                        is Destination.Edit -> TaskEditScreen(
                            container = container,
                            taskId = d.taskId,
                            showBackButton = true,
                            onBack = { destination = Destination.List },
                            onDone = { destination = Destination.List },
                        )
                        Destination.Settings -> SettingsScreen(
                            container = container,
                            showBackButton = true,
                            onBack = { destination = Destination.List },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDetail() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.empty_detail),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
