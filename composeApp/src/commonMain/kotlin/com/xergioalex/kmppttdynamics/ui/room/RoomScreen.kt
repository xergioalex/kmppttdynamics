package com.xergioalex.kmppttdynamics.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.xergioalex.kmppttdynamics.ui.room.tabs.TriviaTab
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
import kmppttdynamics.composeapp.generated.resources.room_members_role_owner
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
import kmppttdynamics.composeapp.generated.resources.tab_trivia
import org.jetbrains.compose.resources.stringResource

private enum class RoomTab { MEMBERS, HAND, CHAT, QA, POLLS, RAFFLES, TRIVIA }

@Composable
fun RoomScreen(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    onLeave: () -> Unit,
    /**
     * Fired when the user taps their own avatar chip in the header.
     * The host of the navigation (App.kt) keeps the active room on
     * the stack and bounces back here once the profile editor closes,
     * so the user can swap avatar / name without leaving the meetup.
     */
    onEditProfile: () -> Unit = {},
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

    val profile by container.settings.profile.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        Header(
            state = state,
            profileName = profile?.displayName,
            profileAvatarId = profile?.avatarId,
            onEditProfile = onEditProfile,
        )

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
            Tab(
                selected = tab == RoomTab.TRIVIA,
                onClick = { tab = RoomTab.TRIVIA },
                text = { Text(stringResource(Res.string.tab_trivia)) },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                RoomTab.MEMBERS -> MembersTab(
                    state = state,
                    isHost = isHost,
                    isOwner = isOwner,
                    ownerClientId = ownerClientId,
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
                RoomTab.TRIVIA -> TriviaTab(
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
private fun Header(
    state: RoomUiState,
    profileName: String?,
    profileAvatarId: Int?,
    onEditProfile: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        state.meetup?.let { meetup ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meetup.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (profileAvatarId != null) {
                    Spacer(Modifier.size(8.dp))
                    SelfProfileChip(
                        displayName = profileName.orEmpty(),
                        avatarId = profileAvatarId,
                        onClick = onEditProfile,
                    )
                }
            }
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

/**
 * Self-profile chip rendered in the room header. Shows a small avatar
 * plus the user's display name (truncated so a long name doesn't push
 * the title off-screen). Tapping it opens the profile editor — which
 * is otherwise only reachable from the home screen — without leaving
 * the meetup; navigation pops back into this room when the editor
 * closes.
 *
 * Uses `Modifier.clickable` with `role = Role.Button` per the project
 * a11y rule: it announces as a button to TalkBack / VoiceOver despite
 * not being one of the Material `Button` containers (which would be
 * too tall for the header).
 */
@Composable
private fun SelfProfileChip(
    displayName: String,
    avatarId: Int,
    onClick: () -> Unit,
) {
    val shortName = displayName.takeIf { it.isNotBlank() }
        ?.let { if (it.length <= 12) it else it.take(11) + "\u2026" }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            AvatarImage(avatarId = avatarId, size = 26.dp)
        }
        if (shortName != null) {
            Spacer(Modifier.size(8.dp))
            Text(
                shortName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
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
    /**
     * Stable identifier of the owner host's device. Each row compares
     * its own [MeetupParticipant.clientId] against this to render the
     * "owner" pill instead of the generic "host" pill.
     */
    ownerClientId: String?,
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
                        val isParticipantOwner =
                            ownerClientId != null && p.clientId == ownerClientId
                        ParticipantRow(
                            participant = p,
                            avatarId = state.usersByClientId[p.clientId]?.avatarId,
                            isMe = p.id == me.id,
                            isOwnerRow = isParticipantOwner,
                            // Only the owner host can reshape the host
                            // roster. Secondary hosts retain every
                            // other host capability but can't promote
                            // or demote anyone. The owner row itself
                            // can never be demoted.
                            canPromote = isOwner && p.id != me.id && !isParticipantOwner,
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
    /**
     * True when this row belongs to the meetup creator. Renders an
     * "owner" pill with primary-container colors instead of the
     * generic "host" pill so the original creator stays visually
     * distinct from co-hosts promoted later.
     */
    isOwnerRow: Boolean,
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
                Spacer(Modifier.height(4.dp))
                RolePill(role = participant.role, isOwner = isOwnerRow)
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

/**
 * Small colored chip that summarizes a participant's authority in the
 * room. The owner chip uses primary-container so the original creator
 * pops visually; co-hosts use tertiary-container; moderators use
 * secondary-container; plain participants get a muted surface chip so
 * the row stays calm while still announcing the role.
 */
@Composable
private fun RolePill(role: ParticipantRole, isOwner: Boolean) {
    val (label, fg, bg) = when {
        isOwner -> Triple(
            stringResource(Res.string.room_members_role_owner),
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primaryContainer,
        )
        role == ParticipantRole.HOST -> Triple(
            stringResource(Res.string.room_members_role_host),
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        role == ParticipantRole.MODERATOR -> Triple(
            stringResource(Res.string.room_members_role_moderator),
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        )
        else -> Triple(
            stringResource(Res.string.room_members_role_participant),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
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
