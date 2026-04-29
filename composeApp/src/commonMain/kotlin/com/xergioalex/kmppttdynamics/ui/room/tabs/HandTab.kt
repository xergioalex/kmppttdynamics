package com.xergioalex.kmppttdynamics.ui.room.tabs

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.HandStatus
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.domain.RaisedHand
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.hand_action_acknowledge
import kmppttdynamics.composeapp.generated.resources.hand_action_dismiss
import kmppttdynamics.composeapp.generated.resources.hand_action_lower
import kmppttdynamics.composeapp.generated.resources.hand_action_speaking
import kmppttdynamics.composeapp.generated.resources.hand_clear_all
import kmppttdynamics.composeapp.generated.resources.hand_lower
import kmppttdynamics.composeapp.generated.resources.hand_message_hint
import kmppttdynamics.composeapp.generated.resources.hand_queue
import kmppttdynamics.composeapp.generated.resources.hand_queue_empty
import kmppttdynamics.composeapp.generated.resources.hand_raise
import kmppttdynamics.composeapp.generated.resources.hand_status_acknowledged
import kmppttdynamics.composeapp.generated.resources.hand_status_raised
import kmppttdynamics.composeapp.generated.resources.hand_status_speaking
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun HandTab(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    participantsById: Map<String, MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var hands by remember { mutableStateOf<List<RaisedHand>?>(null) }
    var draftMessage by remember { mutableStateOf("") }
    val isHost = me.role == ParticipantRole.HOST
    val myActiveHand = hands?.firstOrNull { it.participantId == me.id }

    LaunchedEffect(meetupId) {
        container.hands.observe(meetupId)
            .catch { hands = emptyList() }
            .collect { hands = it }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        if (myActiveHand == null) {
            OutlinedTextField(
                value = draftMessage,
                onValueChange = { draftMessage = it.take(140) },
                placeholder = { Text(stringResource(Res.string.hand_message_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val msg = draftMessage
                    draftMessage = ""
                    scope.launch {
                        runCatching { container.hands.raise(meetupId, me.id, msg) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.hand_raise)) }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        statusLabel(myActiveHand.status),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!myActiveHand.message.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(myActiveHand.message)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch { runCatching { container.hands.lower(myActiveHand.id) } }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.hand_lower)) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.hand_queue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // Host-only one-shot to wipe every active hand. Only
            // visible when the queue actually has something to clear.
            if (isHost && !hands.isNullOrEmpty()) {
                TextButton(onClick = {
                    scope.launch { runCatching { container.hands.clearAll(meetupId) } }
                }) {
                    Text(stringResource(Res.string.hand_clear_all))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                hands == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
                hands!!.isEmpty() -> Text(
                    stringResource(Res.string.hand_queue_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hands!!, key = { it.id }) { hand ->
                        val participant = participantsById[hand.participantId]
                        val avatarId = participant?.clientId?.let { usersByClientId[it]?.avatarId }
                        HandRow(
                            hand = hand,
                            whoBy = participant?.displayName.orEmpty(),
                            avatarId = avatarId,
                            isHost = isHost,
                            onAcknowledge = {
                                scope.launch { runCatching { container.hands.acknowledge(hand.id) } }
                            },
                            onSpeaking = {
                                scope.launch { runCatching { container.hands.setSpeaking(hand.id) } }
                            },
                            onLower = {
                                scope.launch { runCatching { container.hands.lower(hand.id) } }
                            },
                            onDismiss = {
                                scope.launch { runCatching { container.hands.dismiss(hand.id) } }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HandRow(
    hand: RaisedHand,
    whoBy: String,
    avatarId: Int?,
    isHost: Boolean,
    onAcknowledge: () -> Unit,
    onSpeaking: () -> Unit,
    onLower: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarOrPlaceholder(avatarId = avatarId, size = 36.dp)
                Spacer(Modifier.size(10.dp))
                Text(whoBy, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    statusLabel(hand.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (!hand.message.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(hand.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 46.dp))
            }
            if (isHost) {
                Spacer(Modifier.height(8.dp))
                // Horizontally scrollable so 4+ buttons never wrap their
                // own labels into 3 lines like the previous "Dis mis s"
                // overflow.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hand.status == HandStatus.RAISED) {
                        OutlinedButton(onClick = onAcknowledge) {
                            Text(stringResource(Res.string.hand_action_acknowledge))
                        }
                    }
                    if (hand.status == HandStatus.ACKNOWLEDGED) {
                        OutlinedButton(onClick = onSpeaking) {
                            Text(stringResource(Res.string.hand_action_speaking))
                        }
                    }
                    OutlinedButton(onClick = onLower) {
                        Text(stringResource(Res.string.hand_action_lower))
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.hand_action_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: HandStatus): String = when (status) {
    HandStatus.RAISED -> stringResource(Res.string.hand_status_raised)
    HandStatus.ACKNOWLEDGED -> stringResource(Res.string.hand_status_acknowledged)
    HandStatus.SPEAKING -> stringResource(Res.string.hand_status_speaking)
    else -> status.name.lowercase()
}
