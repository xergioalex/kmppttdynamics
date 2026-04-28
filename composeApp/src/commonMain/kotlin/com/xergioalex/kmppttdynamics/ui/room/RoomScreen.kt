package com.xergioalex.kmppttdynamics.ui.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.MeetupStatus
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.room_host_actions
import kmppttdynamics.composeapp.generated.resources.room_host_end
import kmppttdynamics.composeapp.generated.resources.room_host_pause
import kmppttdynamics.composeapp.generated.resources.room_host_resume
import kmppttdynamics.composeapp.generated.resources.room_host_start
import kmppttdynamics.composeapp.generated.resources.room_join_code
import kmppttdynamics.composeapp.generated.resources.room_leave
import kmppttdynamics.composeapp.generated.resources.room_no_participants
import kmppttdynamics.composeapp.generated.resources.room_online
import kmppttdynamics.composeapp.generated.resources.room_participants
import kmppttdynamics.composeapp.generated.resources.room_status_archived
import kmppttdynamics.composeapp.generated.resources.room_status_draft
import kmppttdynamics.composeapp.generated.resources.room_status_ended
import kmppttdynamics.composeapp.generated.resources.room_status_live
import kmppttdynamics.composeapp.generated.resources.room_status_paused
import org.jetbrains.compose.resources.stringResource

@Composable
fun RoomScreen(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    onLeave: () -> Unit,
) {
    val vm: RoomViewModel = viewModel(key = "room-$meetupId-${me.id}") {
        RoomViewModel(container.meetups, container.participants, meetupId, me.id)
    }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        state.meetup?.let { meetup ->
            Text(meetup.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(meetup.status)
                Spacer(Modifier.height(0.dp))
                Text(
                    text = stringResource(Res.string.room_join_code, meetup.joinCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.room_participants, state.totalCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.room_online, state.onlineCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.participants.isEmpty()) {
                Text(
                    stringResource(Res.string.room_no_participants),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.participants, key = { it.id }) { p ->
                        ParticipantRow(participant = p, isMe = p.id == me.id)
                    }
                }
            }
        }

        if (me.role == ParticipantRole.HOST) {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.room_host_actions),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            HostControls(
                status = state.meetup?.status,
                onStart = { vm.setStatus(MeetupStatus.LIVE) },
                onPause = { vm.setStatus(MeetupStatus.PAUSED) },
                onEnd = { vm.setStatus(MeetupStatus.ENDED) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { vm.leave(onLeave) }) {
                Text(stringResource(Res.string.room_leave))
            }
        }
    }
}

@Composable
private fun ParticipantRow(participant: MeetupParticipant, isMe: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(10.dp)
                    .padding(end = 12.dp),
            ) {
                Surface(
                    color = if (participant.isOnline) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                    modifier = Modifier.height(10.dp).clip(CircleShape),
                    content = { Box(Modifier.height(10.dp)) },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = participant.displayName + if (isMe) "  ·  you" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = participant.role.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: MeetupStatus) {
    val (label, color) = when (status) {
        MeetupStatus.DRAFT    -> Res.string.room_status_draft to MaterialTheme.colorScheme.outline
        MeetupStatus.LIVE     -> Res.string.room_status_live to MaterialTheme.colorScheme.secondary
        MeetupStatus.PAUSED   -> Res.string.room_status_paused to MaterialTheme.colorScheme.tertiary
        MeetupStatus.ENDED    -> Res.string.room_status_ended to MaterialTheme.colorScheme.outline
        MeetupStatus.ARCHIVED -> Res.string.room_status_archived to MaterialTheme.colorScheme.outline
    }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HostControls(
    status: MeetupStatus?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onEnd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (status) {
            MeetupStatus.LIVE -> {
                OutlinedButton(onClick = onPause) { Text(stringResource(Res.string.room_host_pause)) }
                OutlinedButton(onClick = onEnd) { Text(stringResource(Res.string.room_host_end)) }
            }
            MeetupStatus.PAUSED -> {
                OutlinedButton(onClick = onStart) { Text(stringResource(Res.string.room_host_resume)) }
                OutlinedButton(onClick = onEnd) { Text(stringResource(Res.string.room_host_end)) }
            }
            MeetupStatus.DRAFT, null -> {
                OutlinedButton(onClick = onStart) { Text(stringResource(Res.string.room_host_start)) }
            }
            else -> Unit // ended / archived → no host actions
        }
    }
}
