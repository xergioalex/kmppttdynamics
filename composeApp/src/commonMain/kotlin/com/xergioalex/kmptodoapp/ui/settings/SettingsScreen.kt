package com.xergioalex.kmptodoapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xergioalex.kmptodoapp.AppContainer
import com.xergioalex.kmptodoapp.settings.ThemeMode
import org.jetbrains.compose.resources.stringResource
import kmptodoapp.composeapp.generated.resources.Res
import kmptodoapp.composeapp.generated.resources.action_back
import kmptodoapp.composeapp.generated.resources.action_settings
import kmptodoapp.composeapp.generated.resources.settings_theme
import kmptodoapp.composeapp.generated.resources.settings_theme_dark
import kmptodoapp.composeapp.generated.resources.settings_theme_light
import kmptodoapp.composeapp.generated.resources.settings_theme_system

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    showBackButton: Boolean,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settings) }
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.action_settings)) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                        }
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
            )
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.SYSTEM -> stringResource(Res.string.settings_theme_system)
                    ThemeMode.LIGHT -> stringResource(Res.string.settings_theme_light)
                    ThemeMode.DARK -> stringResource(Res.string.settings_theme_dark)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = mode == theme,
                            role = Role.RadioButton,
                            onClick = { viewModel.setThemeMode(mode) },
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == theme, onClick = null)
                    Text(label, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
