package com.xergioalex.kmppttdynamics.ui.room.tabs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.domain.Raffle
import com.xergioalex.kmppttdynamics.domain.RaffleEntry
import com.xergioalex.kmppttdynamics.domain.RaffleStatus
import com.xergioalex.kmppttdynamics.domain.RaffleWinner
import com.xergioalex.kmppttdynamics.raffles.RaffleBoard
import com.xergioalex.kmppttdynamics.ui.components.AvatarImage
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.raffles_close
import kmppttdynamics.composeapp.generated.resources.raffles_create
import kmppttdynamics.composeapp.generated.resources.raffles_draw_winner
import kmppttdynamics.composeapp.generated.resources.raffles_empty
import kmppttdynamics.composeapp.generated.resources.raffles_enroll_all
import kmppttdynamics.composeapp.generated.resources.raffles_enter
import kmppttdynamics.composeapp.generated.resources.raffles_entered
import kmppttdynamics.composeapp.generated.resources.raffles_entries
import kmppttdynamics.composeapp.generated.resources.raffles_field_title
import kmppttdynamics.composeapp.generated.resources.raffles_no_entries
import kmppttdynamics.composeapp.generated.resources.raffles_status_closed
import kmppttdynamics.composeapp.generated.resources.raffles_status_drawn
import kmppttdynamics.composeapp.generated.resources.raffles_status_open
import kmppttdynamics.composeapp.generated.resources.raffles_winner
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun RafflesTab(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    participantsById: Map<String, MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<RaffleBoard?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val isHost = me.role == ParticipantRole.HOST

    LaunchedEffect(meetupId) {
        container.raffles.observeBoard(meetupId)
            .catch { board = RaffleBoard(emptyList(), emptyMap(), emptyMap()) }
            .collect { board = it }
    }

    // Tiny helper that runs a suspending action and displays any
    // exception inline. Keeps the UI from silently swallowing errors
    // when the user clicks Enter / Draw / Enroll-all.
    fun runAction(label: String, block: suspend () -> Unit) {
        actionError = null
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                println("RafflesTab[$label] failed: ${t::class.simpleName}: ${t.message}")
                actionError = "$label failed: ${t.message ?: t::class.simpleName}"
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        if (isHost) {
            Button(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.raffles_create)) }
            Spacer(Modifier.height(12.dp))
        }
        actionError?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                board == null -> Text(
                    "…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                board!!.raffles.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(Res.string.raffles_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(board!!.raffles, key = { it.id }) { raffle ->
                        RaffleCard(
                            raffle = raffle,
                            entries = board!!.entries[raffle.id].orEmpty(),
                            winners = board!!.winners[raffle.id].orEmpty(),
                            meId = me.id,
                            isHost = isHost,
                            participantsById = participantsById,
                            usersByClientId = usersByClientId,
                            onEnter = {
                                runAction("enter") {
                                    container.raffles.enter(raffle.id, me.id)
                                }
                            },
                            onEnrollAll = {
                                runAction("enroll-all") {
                                    container.raffles.enrollAllParticipants(raffle.id, meetupId)
                                }
                            },
                            onDraw = {
                                runAction("draw") {
                                    container.raffles.drawWinner(raffle.id)
                                }
                            },
                            onClose = {
                                runAction("close") {
                                    container.raffles.close(raffle.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateRaffleDialog(
            onDismiss = { showCreate = false },
            onCreate = { title ->
                showCreate = false
                runAction("create") {
                    container.raffles.create(meetupId, me.id, title)
                }
            },
        )
    }
}

@Composable
private fun RaffleCard(
    raffle: Raffle,
    entries: List<RaffleEntry>,
    winners: List<RaffleWinner>,
    meId: String,
    isHost: Boolean,
    participantsById: Map<String, MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
    onEnter: () -> Unit,
    onEnrollAll: () -> Unit,
    onDraw: () -> Unit,
    onClose: () -> Unit,
) {
    val iAmIn = entries.any { it.participantId == meId }
    val firstWinner = winners.firstOrNull()
    val winnerParticipant = firstWinner?.let { participantsById[it.participantId] }
    val winnerName = winnerParticipant?.displayName
    val winnerAvatar = winnerParticipant?.clientId?.let { usersByClientId[it]?.avatarId }

    fun avatarFor(participantId: String): Int? {
        val p = participantsById[participantId] ?: return null
        return p.clientId?.let { usersByClientId[it]?.avatarId }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    raffle.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    statusLabel(raffle.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(Res.string.raffles_entries, entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entries.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.raffles_no_entries),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                // Visual stack of entry avatars (up to 6) so the host can
                // see at a glance who's already in the pool.
                EntryAvatarStack(
                    avatarIds = entries.take(6).mapNotNull { avatarFor(it.participantId) },
                    extra = (entries.size - 6).coerceAtLeast(0),
                )
            }

            // Winner reveal — animates in when a winner arrives.
            if (winnerName != null && raffle.status == RaffleStatus.DRAWN) {
                Spacer(Modifier.height(10.dp))
                WinnerReveal(name = winnerName, avatarId = winnerAvatar)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (raffle.status.acceptsEntries) {
                    if (iAmIn) {
                        Text(
                            stringResource(Res.string.raffles_entered),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    } else {
                        Button(onClick = onEnter) {
                            Text(stringResource(Res.string.raffles_enter))
                        }
                    }
                }
                if (isHost) {
                    if (raffle.status == RaffleStatus.OPEN) {
                        OutlinedButton(onClick = onEnrollAll) {
                            Text(stringResource(Res.string.raffles_enroll_all))
                        }
                        OutlinedButton(onClick = onDraw, enabled = entries.isNotEmpty()) {
                            Text(stringResource(Res.string.raffles_draw_winner))
                        }
                    }
                    if (raffle.status == RaffleStatus.DRAWN) {
                        OutlinedButton(onClick = onClose) {
                            Text(stringResource(Res.string.raffles_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WinnerReveal(name: String, avatarId: Int?) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "winnerReveal",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (avatarId != null) {
            AvatarImage(avatarId = avatarId, size = 84.dp)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            stringResource(Res.string.raffles_winner, name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/**
 * Stacked avatar bubbles — Discord-style "who's in the room" preview.
 * Shows up to 5 avatars overlapping, plus a `+N` chip if there are
 * more. Avatars are rendered in reverse order so the leftmost one
 * appears on top.
 */
@Composable
private fun EntryAvatarStack(avatarIds: List<Int>, extra: Int) {
    if (avatarIds.isEmpty() && extra == 0) return
    val overlap = (-12).dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        avatarIds.forEachIndexed { index, id ->
            Box(
                modifier = Modifier
                    .padding(start = if (index == 0) 0.dp else overlap)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp),
            ) {
                AvatarImage(avatarId = id, size = 28.dp)
            }
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .padding(start = overlap)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+$extra",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: RaffleStatus): String = when (status) {
    RaffleStatus.OPEN -> stringResource(Res.string.raffles_status_open)
    RaffleStatus.DRAWN -> stringResource(Res.string.raffles_status_drawn)
    RaffleStatus.CLOSED -> stringResource(Res.string.raffles_status_closed)
    else -> status.name.lowercase()
}

@Composable
private fun CreateRaffleDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.raffles_create)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(Res.string.raffles_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onCreate(title) },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(Res.string.raffles_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
