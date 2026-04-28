package com.xergioalex.kmppttdynamics.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.join_action_join
import kmppttdynamics.composeapp.generated.resources.join_display_name
import kmppttdynamics.composeapp.generated.resources.join_role_host
import kmppttdynamics.composeapp.generated.resources.join_title
import kmppttdynamics.composeapp.generated.resources.room_join_code
import org.jetbrains.compose.resources.stringResource

@Composable
fun JoinMeetupScreen(
    container: AppContainer,
    meetupId: String,
    initialDisplayName: String,
    onCancel: () -> Unit,
    onJoined: (MeetupParticipant) -> Unit,
) {
    val vm: JoinMeetupViewModel = viewModel(key = "join-$meetupId") {
        JoinMeetupViewModel(container.meetups, container.participants, initialDisplayName, meetupId)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.joined) {
        state.joined?.let {
            onJoined(it)
            vm.consumeJoined()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.join_title), style = MaterialTheme.typography.headlineSmall)
        state.meetup?.let { meetup ->
            Text(meetup.title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.room_join_code, meetup.joinCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = state.displayName,
            onValueChange = vm::onDisplayName,
            singleLine = true,
            label = { Text(stringResource(Res.string.join_display_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = state.joinAsHost, onCheckedChange = vm::onJoinAsHost)
            Spacer(Modifier.height(0.dp))
            Text(
                stringResource(Res.string.join_role_host),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (state.errorMessage != null) {
            Text(
                state.errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
            Button(onClick = vm::submit, enabled = state.canSubmit) {
                Text(stringResource(Res.string.join_action_join))
            }
        }
    }
}
