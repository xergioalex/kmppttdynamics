package com.xergioalex.kmppttdynamics.ui.room.tabs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.ChatMessage
import com.xergioalex.kmppttdynamics.domain.ChatStatus
import com.xergioalex.kmppttdynamics.domain.ChatType
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.chat_announce
import kmppttdynamics.composeapp.generated.resources.chat_announce_hint
import kmppttdynamics.composeapp.generated.resources.chat_announce_label
import kmppttdynamics.composeapp.generated.resources.chat_empty
import kmppttdynamics.composeapp.generated.resources.chat_hide
import kmppttdynamics.composeapp.generated.resources.chat_input_hint
import kmppttdynamics.composeapp.generated.resources.chat_send
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatTab(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var announcing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isHost = me.role == ParticipantRole.HOST

    LaunchedEffect(meetupId) {
        container.chat.observe(meetupId)
            .catch { /* surface errors via shared snackbar later */ }
            .collect { messages = it }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Text(
                    stringResource(Res.string.chat_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageRow(
                            msg = msg,
                            isMine = msg.participantId == me.id,
                            canHide = isHost,
                            onHide = { scope.launch { runCatching { container.chat.hide(msg.id) } } },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (isHost && announcing) {
            AnnouncementInput(
                onCancel = { announcing = false },
                onSend = { text ->
                    if (text.isNotBlank()) {
                        scope.launch {
                            runCatching {
                                container.chat.sendAnnouncement(meetupId, me.id, text)
                            }
                        }
                        announcing = false
                    }
                },
            )
        } else {
            ChatComposer(
                value = input,
                onChange = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        val text = input
                        input = ""
                        scope.launch {
                            runCatching { container.chat.send(meetupId, me.id, text) }
                        }
                    }
                },
                onAnnounce = if (isHost) ({ announcing = true }) else null,
            )
        }
    }
}

@Composable
private fun MessageRow(
    msg: ChatMessage,
    isMine: Boolean,
    canHide: Boolean,
    onHide: () -> Unit,
) {
    val isAnnouncement = msg.type == ChatType.ANNOUNCEMENT
    val isHidden = msg.status == ChatStatus.HIDDEN
    val container = when {
        isAnnouncement -> MaterialTheme.colorScheme.secondaryContainer
        isMine -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when {
        isAnnouncement -> MaterialTheme.colorScheme.onSecondaryContainer
        isMine -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine && !isAnnouncement) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(container)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (isAnnouncement) {
                Text(
                    stringResource(Res.string.chat_announce_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (isHidden) "—" else msg.message,
                color = onContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (canHide && !isHidden) {
                TextButton(onClick = onHide) {
                    Text(stringResource(Res.string.chat_hide), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onAnnounce: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(stringResource(Res.string.chat_input_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (onAnnounce != null) {
            AssistChip(
                onClick = onAnnounce,
                label = { Text(stringResource(Res.string.chat_announce)) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.secondary,
                ),
            )
        }
        IconButton(onClick = onSend, enabled = value.isNotBlank()) {
            Text("↑", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AnnouncementInput(onCancel: () -> Unit, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(stringResource(Res.string.chat_announce_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.action_cancel))
            }
            TextButton(onClick = { onSend(draft) }, enabled = draft.isNotBlank()) {
                Text(stringResource(Res.string.chat_announce))
            }
        }
    }
}
