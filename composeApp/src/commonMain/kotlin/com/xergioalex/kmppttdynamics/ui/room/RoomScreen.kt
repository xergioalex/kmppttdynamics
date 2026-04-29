package com.xergioalex.kmppttdynamics.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.xergioalex.kmppttdynamics.ui.components.AvatarImage
import com.xergioalex.kmppttdynamics.ui.room.tabs.ChatTab
import com.xergioalex.kmppttdynamics.ui.room.tabs.HandTab
import com.xergioalex.kmppttdynamics.ui.room.tabs.PollsTab
import com.xergioalex.kmppttdynamics.ui.room.tabs.QATab
import com.xergioalex.kmppttdynamics.ui.room.tabs.RafflesTab
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.room_back
import kmppttdynamics.composeapp.generated.resources.room_back_helper
import kmppttdynamics.composeapp.generated.resources.room_host_actions
import kmppttdynamics.composeapp.generated.resources.room_host_end
import kmppttdynamics.composeapp.generated.resources.room_host_pause
import kmppttdynamics.composeapp.generated.resources.room_host_resume
import kmppttdynamics.composeapp.generated.resources.room_host_start
import kmppttdynamics.composeapp.generated.resources.room_join_code
import kmppttdynamics.composeapp.generated.resources.room_leave
import kmppttdynamics.composeapp.generated.resources.room_members_demote
import kmppttdynamics.composeapp.generated.resources.room_members_promote
import kmppttdynamics.composeapp.generated.resources.room_members_role_host
import kmppttdynamics.composeapp.generated.resources.room_members_role_moderator
import kmppttdynamics.composeapp.generated.resources.room_members_role_participant
import kmppttdynamics.composeapp.generated.resources.room_members_you
import kmppttdynamics.composeapp.generated.resources.room_no_participants
import kmppttdynamics.composeapp.generated.resources.room_online
import kmppttdynamics.composeapp.generated.resources.room_participants
import kmppttdynamics.composeapp.generated.resources.room_status_archived
import kmppttdynamics.composeapp.generated.resources.room_status_draft
import kmppttdynamics.composeapp.generated.resources.room_status_ended
import kmppttdynamics.composeapp.generated.resources.room_status_live
import kmppttdynamics.composeapp.generated.resources.room_status_paused
import kmppttdynamics.composeapp.generated.resources.tab_chat
import kmppttdynamics.composeapp.generated.resources.tab_hand
import kmppttdynamics.composeapp.generated.resources.tab_members
import kmppttdynamics.composeapp.generated.resources.tab_polls
import kmppttdynamics.composeapp.generated.resources.tab_qa
import kmppttdynamics.composeapp.generated.resources.tab_raffles
import org.jetbrains.compose.resources.stringResource

private enum class RoomTab { MEMBERS, HAND, CHAT, QA, POLLS, RAFFLES }

@Composable
fun RoomScreen(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    onLeave: () -> Unit,
) {
    val vm: RoomViewModel = viewModel(key = "room-$meetupId-${me.id}") {
        RoomViewModel(
            meetups = container.meetups,
            participants = container.participants,
            users = container.users,
            meetupId = meetupId,
            mySelfId = me.id,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(RoomTab.MEMBERS) }
    val participantsById = remember(state.participants) {
        state.participants.associateBy { it.id }
    }
    // The "me" prop carries the role we joined with; the realtime feed
    // can promote/demote us, so always trust the latest server snapshot.
    val liveMe = participantsById[me.id] ?: me
    val isHost = liveMe.role == ParticipantRole.HOST
    // The "owner" host is the original creator of the meetup. We
    // identify them as the HOST participant with the earliest
    // joined_at — no schema changes needed. Only this device gets
    // promote/demote controls in the Members tab; secondary hosts
    // get host capabilities (chat moderation, polls, raffles) but
    // cannot reshape the host roster.
    val ownerClientId = remember(state.participants) {
        state.participants
            .filter { it.role == ParticipantRole.HOST }
            .minByOrNull { it.joinedAt }
            ?.clientId
    }
    val isOwner = liveMe.clientId != null && liveMe.clientId == ownerClientId
    var showLeaveDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        Header(state)

        PrimaryScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            edgePadding = 12.dp,
        ) {
            Tab(
                selected = tab == RoomTab.MEMBERS,
                onClick = { tab = RoomTab.MEMBERS },
                text = { Text(stringResource(Res.string.tab_members)) },
            )
            Tab(
                selected = tab == RoomTab.HAND,
                onClick = { tab = RoomTab.HAND },
                text = { Text(stringResource(Res.string.tab_hand)) },
            )
            Tab(
                selected = tab == RoomTab.CHAT,
                onClick = { tab = RoomTab.CHAT },
                text = { Text(stringResource(Res.string.tab_chat)) },
            )
            Tab(
                selected = tab == RoomTab.QA,
                onClick = { tab = RoomTab.QA },
                text = { Text(stringResource(Res.string.tab_qa)) },
            )
            Tab(
                selected = tab == RoomTab.POLLS,
                onClick = { tab = RoomTab.POLLS },
                text = { Text(stringResource(Res.string.tab_polls)) },
            )
            Tab(
                selected = tab == RoomTab.RAFFLES,
                onClick = { tab = RoomTab.RAFFLES },
                text = { Text(stringResource(Res.string.tab_raffles)) },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                RoomTab.MEMBERS -> MembersTab(
                    state = state,
                    isHost = isHost,
                    isOwner = isOwner,
                    me = liveMe,
                    onSetStatus = vm::setStatus,
                    onSetRole = vm::setParticipantRole,
                )
                RoomTab.HAND -> HandTab(
                    container = container,
                    meetupId = meetupId,
                    me = liveMe,
                    participantsById = participantsById,
                    usersByClientId = state.usersByClientId,
                )
                RoomTab.CHAT -> ChatTab(
                    container = container,
                    meetupId = meetupId,
                    me = liveMe,
                    participantsById = participantsById,
                    usersByClientId = state.usersByClientId,
                )
                RoomTab.QA -> QATab(
                    container = container,
                    meetupId = meetupId,
                    me = liveMe,
                    participantsById = participantsById,
                    usersByClientId = state.usersByClientId,
                )
                RoomTab.POLLS -> PollsTab(
                    container = container,
                    meetupId = meetupId,
                    me = liveMe,
                    participantsById = participantsById,
                    usersByClientId = state.usersByClientId,
                )
                RoomTab.RAFFLES -> RafflesTab(
                    container = container,
                    meetupId = meetupId,
                    me = liveMe,
                    participantsById = participantsById,
                    usersByClientId = state.usersByClientId,
                )
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { showLeaveDialog = true }) {
                Text(stringResource(Res.string.room_leave))
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(Res.string.room_back)) },
            text = { Text(stringResource(Res.string.room_back_helper)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    vm.leave(onLeave)
                }) {
                    Text(stringResource(Res.string.room_back))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun Header(state: RoomUiState) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        state.meetup?.let { meetup ->
            Text(meetup.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(meetup.status)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(Res.string.room_join_code, meetup.joinCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                OnlineBadge(state.onlineCount)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OnlineBadge(count: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(Res.string.room_online, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MembersTab(
    state: RoomUiState,
    isHost: Boolean,
    /**
     * The owner host (original meetup creator) is the only one who can
     * reshape the host roster. Secondary hosts can moderate chat, run
     * polls and raffles, but the promote / demote controls in the
     * member list stay hidden for them.
     */
    isOwner: Boolean,
    me: MeetupParticipant,
    onSetStatus: (MeetupStatus) -> Unit,
    onSetRole: (participantId: String, role: ParticipantRole) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
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
                    // Show online participants first, then offline.
                    val sorted = state.participants.sortedWith(
                        compareByDescending<MeetupParticipant> { it.isOnline }
                            .thenBy { it.role.ordinal }
                            .thenBy { it.joinedAt },
                    )
                    items(sorted, key = { it.id }) { p ->
                        ParticipantRow(
                            participant = p,
                            avatarId = state.usersByClientId[p.clientId]?.avatarId,
                            isMe = p.id == me.id,
                            // Only the owner host can reshape the host
                            // roster. Secondary hosts retain every
                            // other host capability but can't promote
                            // or demote anyone.
                            canPromote = isOwner && p.id != me.id,
                            onPromote = { onSetRole(p.id, ParticipantRole.HOST) },
                            onDemote = { onSetRole(p.id, ParticipantRole.PARTICIPANT) },
                        )
                    }
                }
            }
        }

        if (isHost) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.room_host_actions),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            HostControls(
                status = state.meetup?.status,
                onStart = { onSetStatus(MeetupStatus.LIVE) },
                onPause = { onSetStatus(MeetupStatus.PAUSED) },
                onEnd = { onSetStatus(MeetupStatus.ENDED) },
            )
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: MeetupParticipant,
    avatarId: Int?,
    isMe: Boolean,
    canPromote: Boolean,
    onPromote: () -> Unit,
    onDemote: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (avatarId != null) {
                    AvatarImage(avatarId = avatarId, size = 44.dp)
                } else {
                    // Pre-onboarding rows (legacy) — fall back to a
                    // simple monochrome circle so the row layout stays
                    // consistent with everyone else's.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                // Presence dot overlapping the avatar's bottom-right.
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (participant.isOnline) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.outline,
                            ),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMe) {
                        "${participant.displayName} · ${stringResource(Res.string.room_members_you)}"
                    } else {
                        participant.displayName
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = roleLabel(participant.role),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canPromote) {
                if (participant.role == ParticipantRole.HOST) {
                    TextButton(onClick = onDemote) {
                        Text(stringResource(Res.string.room_members_demote))
                    }
                } else {
                    TextButton(onClick = onPromote) {
                        Text(stringResource(Res.string.room_members_promote))
                    }
                }
            }
        }
    }
}

@Composable
private fun roleLabel(role: ParticipantRole): String = when (role) {
    ParticipantRole.HOST -> stringResource(Res.string.room_members_role_host)
    ParticipantRole.MODERATOR -> stringResource(Res.string.room_members_role_moderator)
    ParticipantRole.PARTICIPANT -> stringResource(Res.string.room_members_role_participant)
}

@Composable
private fun StatusPill(status: MeetupStatus) {
    val (label, fg, bg) = when (status) {
        MeetupStatus.DRAFT -> Triple(
            stringResource(Res.string.room_status_draft),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
        )
        MeetupStatus.LIVE -> Triple(
            stringResource(Res.string.room_status_live),
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        )
        MeetupStatus.PAUSED -> Triple(
            stringResource(Res.string.room_status_paused),
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        MeetupStatus.ENDED -> Triple(
            stringResource(Res.string.room_status_ended),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
        )
        MeetupStatus.ARCHIVED -> Triple(
            stringResource(Res.string.room_status_archived),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
        )
    }
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
            else -> Unit
        }
    }
}
