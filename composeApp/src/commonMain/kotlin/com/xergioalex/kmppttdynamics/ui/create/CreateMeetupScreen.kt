package com.xergioalex.kmppttdynamics.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.Meetup
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.create_action_create
import kmppttdynamics.composeapp.generated.resources.create_field_description
import kmppttdynamics.composeapp.generated.resources.create_field_join_code
import kmppttdynamics.composeapp.generated.resources.create_field_join_code_helper
import kmppttdynamics.composeapp.generated.resources.create_field_title
import kmppttdynamics.composeapp.generated.resources.create_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateMeetupScreen(
    container: AppContainer,
    onCancel: () -> Unit,
    onCreated: (Meetup, MeetupParticipant) -> Unit,
) {
    val vm: CreateMeetupViewModel = viewModel {
        CreateMeetupViewModel(container.meetups, container.participants, container.settings)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.created) {
        state.created?.let { (meetup, host) ->
            onCreated(meetup, host)
            vm.consumeCreated()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.create_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.title,
            onValueChange = vm::onTitle,
            singleLine = true,
            label = { Text(stringResource(Res.string.create_field_title)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = vm::onDescription,
            label = { Text(stringResource(Res.string.create_field_description)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.joinCode,
            onValueChange = vm::onJoinCode,
            singleLine = true,
            label = { Text(stringResource(Res.string.create_field_join_code)) },
            supportingText = { Text(stringResource(Res.string.create_field_join_code_helper)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.errorMessage != null) {
            Text(
                state.errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel, enabled = !state.isSaving) {
                Text(stringResource(Res.string.action_cancel))
            }
            Button(
                onClick = vm::submit,
                enabled = state.canSubmit,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.create_action_create))
                }
            }
        }
    }
}
