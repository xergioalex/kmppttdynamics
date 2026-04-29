package com.xergioalex.kmppttdynamics.ui.room.tabs

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import kmppttdynamics.composeapp.generated.resources.raffles_drawing
import kmppttdynamics.composeapp.generated.resources.raffles_empty
import kmppttdynamics.composeapp.generated.resources.raffles_enroll_all
import kmppttdynamics.composeapp.generated.resources.raffles_enter
import kmppttdynamics.composeapp.generated.resources.raffles_entered
import kmppttdynamics.composeapp.generated.resources.raffles_entries
import kmppttdynamics.composeapp.generated.resources.raffles_field_title
import kmppttdynamics.composeapp.generated.resources.raffles_no_entries
import kmppttdynamics.composeapp.generated.resources.raffles_relaunch
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
    /**
     * Identifier of the currently-running action. `null` while idle.
     * Used to gate every host button so rapid double-taps don't fan
     * out into a storm of inserts (which used to flood the realtime
     * feed and block the UI thread on the resulting re-fetches).
     */
    var working by remember { mutableStateOf<String?>(null) }
    val isHost = me.role == ParticipantRole.HOST

    LaunchedEffect(meetupId) {
        container.raffles.observeBoard(meetupId)
            .catch { board = RaffleBoard(emptyList(), emptyMap(), emptyMap()) }
            .collect { board = it }
    }

    /**
     * Tiny helper that runs a suspending action exactly once at a
     * time. While [working] is non-null, additional calls bail out
     * — the UI also greys out every action button that participates,
     * but this guard is the safety net.
     */
    fun runAction(label: String, block: suspend () -> Unit) {
        if (working != null) return
        actionError = null
        working = label
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                println("RafflesTab[$label] failed: ${t::class.simpleName}: ${t.message}")
                actionError = "$label failed: ${t.message ?: t::class.simpleName}"
            } finally {
                working = null
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
                            isWorking = working != null,
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
                            onRelaunch = {
                                runAction("relaunch") {
                                    container.raffles.relaunch(raffle.id)
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
    isWorking: Boolean,
    participantsById: Map<String, MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
    onEnter: () -> Unit,
    onEnrollAll: () -> Unit,
    onDraw: () -> Unit,
    onClose: () -> Unit,
    onRelaunch: () -> Unit,
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

            // Winner reveal — for both DRAWN (just announced) and CLOSED
            // (kept around so anyone scrolling later can see the result).
            if (winnerName != null &&
                (raffle.status == RaffleStatus.DRAWN || raffle.status == RaffleStatus.CLOSED)
            ) {
                Spacer(Modifier.height(10.dp))
                WinnerReveal(
                    name = winnerName,
                    avatarId = winnerAvatar,
                    spinAvatarPool = entries.mapNotNull { avatarFor(it.participantId) },
                    // Spin only when the raffle was JUST drawn — closed
                    // raffles are historical, so we just render the
                    // winner statically.
                    animate = raffle.status == RaffleStatus.DRAWN,
                )
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
                        Button(onClick = onEnter, enabled = !isWorking) {
                            Text(stringResource(Res.string.raffles_enter))
                        }
                    }
                }
                if (isHost) {
                    if (raffle.status == RaffleStatus.OPEN) {
                        OutlinedButton(onClick = onEnrollAll, enabled = !isWorking) {
                            Text(stringResource(Res.string.raffles_enroll_all))
                        }
                        OutlinedButton(
                            onClick = onDraw,
                            enabled = !isWorking && entries.isNotEmpty(),
                        ) {
                            Text(stringResource(Res.string.raffles_draw_winner))
                        }
                    }
                    if (raffle.status == RaffleStatus.DRAWN) {
                        OutlinedButton(onClick = onClose, enabled = !isWorking) {
                            Text(stringResource(Res.string.raffles_close))
                        }
                    }
                    if (raffle.status == RaffleStatus.DRAWN ||
                        raffle.status == RaffleStatus.CLOSED
                    ) {
                        OutlinedButton(onClick = onRelaunch, enabled = !isWorking) {
                            Text(stringResource(Res.string.raffles_relaunch))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reveals the raffle winner with a slot-machine style spin: cycles
 * through the entrants' avatars, accelerating then decelerating, and
 * settles on [avatarId] / [name] with a subtle bounce.
 *
 * - When [animate] is `false` (e.g. on a CLOSED raffle scrolled
 *   into view long after the draw) the spin is skipped and the
 *   winner is rendered statically.
 * - When [animate] is `true` (raffle just transitioned to DRAWN),
 *   the spin pulls random avatars from [spinAvatarPool] for ~1.6 s
 *   in three phases (fast → slow → settle) before locking onto the
 *   real winner.
 *
 * The avatar bitmaps are decoded once and cached by `AvatarImage` so
 * the rapid cycling doesn't jank the UI thread.
 */
@Composable
private fun WinnerReveal(
    name: String,
    avatarId: Int?,
    spinAvatarPool: List<Int>,
    animate: Boolean,
) {
    var displayedAvatar by remember(name, animate) { mutableStateOf(avatarId) }
    var label by remember(name, animate) {
        mutableStateOf(if (animate) SpinPhase.Spinning else SpinPhase.Settled)
    }
    val scale = remember(name, animate) { Animatable(if (animate) 0.85f else 1f) }

    LaunchedEffect(name, animate) {
        if (!animate || spinAvatarPool.isEmpty()) {
            displayedAvatar = avatarId
            label = SpinPhase.Settled
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        // Phase 1: fast cycling — 1.0 s @ 80 ms.
        repeat(12) {
            displayedAvatar = spinAvatarPool.random()
            kotlinx.coroutines.delay(80)
        }
        // Phase 2: slowing down — ~600 ms with growing intervals.
        listOf(120L, 160L, 220L, 300L).forEach { wait ->
            displayedAvatar = spinAvatarPool.random()
            kotlinx.coroutines.delay(wait)
        }
        // Phase 3: settle on the real winner with a tiny bounce.
        displayedAvatar = avatarId
        label = SpinPhase.Settled
        scale.animateTo(1.12f, tween(durationMillis = 180))
        scale.animateTo(1f, tween(durationMillis = 220))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (displayedAvatar != null) {
            AvatarImage(avatarId = displayedAvatar!!, size = 84.dp)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = when (label) {
                SpinPhase.Spinning -> stringResource(Res.string.raffles_drawing)
                SpinPhase.Settled -> stringResource(Res.string.raffles_winner, name)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

private enum class SpinPhase { Spinning, Settled }

/**
 * Stacked avatar bubbles — Discord-style "who's in the room" preview.
 * Shows up to 6 avatars overlapping, plus a `+N` chip if there are
 * more.
 *
 * Implemented as a fixed-size [Box] with each tile placed via
 * `Modifier.offset(x = step * index)`. We can't use negative
 * `Modifier.padding(...)` for the overlap because Compose enforces
 * non-negative padding at construction time and crashes the whole
 * composition with `IllegalArgumentException: Padding must be
 * non-negative` — that was the bug that took the raffles tab down.
 *
 * The Box's width is computed explicitly (`ringSize + step * (n - 1)`)
 * because `offset` is a placement-only modifier — Compose's measure
 * pass doesn't expand the parent to fit offset children, so without an
 * explicit width the rightmost avatars would be clipped.
 */
@Composable
private fun EntryAvatarStack(avatarIds: List<Int>, extra: Int) {
    if (avatarIds.isEmpty() && extra == 0) return
    val avatarSize = 28.dp
    val ringSize = 32.dp                // 28 dp avatar + 2 dp white ring on each side
    val step = 22.dp                    // 10 dp overlap between adjacent rings
    val tileCount = avatarIds.size + (if (extra > 0) 1 else 0)
    val totalWidth = ringSize + step * (tileCount - 1).coerceAtLeast(0)

    Box(modifier = Modifier.height(ringSize).width(totalWidth)) {
        avatarIds.forEachIndexed { index, id ->
            Box(
                modifier = Modifier
                    .offset(x = step * index)
                    .size(ringSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp),
            ) {
                AvatarImage(avatarId = id, size = avatarSize)
            }
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .offset(x = step * avatarIds.size)
                    .size(ringSize)
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
