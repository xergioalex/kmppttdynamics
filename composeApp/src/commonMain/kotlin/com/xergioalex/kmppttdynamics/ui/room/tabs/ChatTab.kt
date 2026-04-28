package com.xergioalex.kmppttdynamics.ui.room.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.ChatMessage
import com.xergioalex.kmppttdynamics.domain.ChatStatus
import com.xergioalex.kmppttdynamics.domain.ChatType
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.ui.components.AvatarImage
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.chat_announce_hint
import kmppttdynamics.composeapp.generated.resources.chat_announce_label
import kmppttdynamics.composeapp.generated.resources.chat_announce_toggle
import kmppttdynamics.composeapp.generated.resources.chat_empty
import kmppttdynamics.composeapp.generated.resources.chat_hide
import kmppttdynamics.composeapp.generated.resources.chat_input_hint
import kmppttdynamics.composeapp.generated.resources.chat_loading
import kmppttdynamics.composeapp.generated.resources.chat_send
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatTab(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    participantsById: Map<String, MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<ChatMessage>?>(null) }
    var input by remember { mutableStateOf("") }
    var sendAsAnnouncement by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isHost = me.role == ParticipantRole.HOST

    LaunchedEffect(meetupId) {
        container.chat.observe(meetupId)
            .catch { messages = emptyList() }
            .collect { messages = it }
    }

    LaunchedEffect(messages?.size) {
        val list = messages
        if (!list.isNullOrEmpty()) {
            listState.animateScrollToItem(list.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                messages == null -> LoadingState(stringResource(Res.string.chat_loading))
                messages!!.isEmpty() -> Text(
                    stringResource(Res.string.chat_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(messages!!, key = { it.id }) { msg ->
                        val participant = msg.participantId?.let { participantsById[it] }
                        val avatarId = participant?.clientId?.let { usersByClientId[it]?.avatarId }
                        MessageRow(
                            msg = msg,
                            displayName = participant?.displayName ?: "—",
                            avatarId = avatarId,
                            isMine = msg.participantId == me.id,
                            canHide = isHost,
                            onHide = {
                                scope.launch { runCatching { container.chat.hide(msg.id) } }
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ChatComposer(
            value = input,
            onChange = { input = it },
            isHost = isHost,
            sendAsAnnouncement = sendAsAnnouncement,
            onToggleAnnounce = { sendAsAnnouncement = it },
            onSend = {
                val text = input.trim()
                if (text.isBlank()) return@ChatComposer
                input = ""
                val announce = sendAsAnnouncement
                sendAsAnnouncement = false
                scope.launch {
                    runCatching {
                        if (announce && isHost) {
                            container.chat.sendAnnouncement(meetupId, me.id, text)
                        } else {
                            container.chat.send(meetupId, me.id, text)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun LoadingState(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageRow(
    msg: ChatMessage,
    displayName: String,
    avatarId: Int?,
    isMine: Boolean,
    canHide: Boolean,
    onHide: () -> Unit,
) {
    val isAnnouncement = msg.type == ChatType.ANNOUNCEMENT
    val isHidden = msg.status == ChatStatus.HIDDEN

    if (isAnnouncement) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AvatarOrPlaceholder(avatarId = avatarId, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.chat_announce_label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isHidden) "—" else msg.message,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (canHide && !isHidden) {
                    TextButton(onClick = onHide, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(stringResource(Res.string.chat_hide), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            AvatarOrPlaceholder(avatarId = avatarId, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isMine) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isMine) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = if (isHidden) "—" else msg.message,
                        color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (canHide && !isHidden && !isMine) {
                    TextButton(onClick = onHide, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(
                            stringResource(Res.string.chat_hide),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small helper that renders [AvatarImage] when an [avatarId] is known
 * and a circular placeholder otherwise. Used everywhere a row owns an
 * avatar — chat, Q&A, hand queue, raffles — so pre-onboarding rows
 * (no `client_id`) render with the same shape.
 */
@Composable
internal fun AvatarOrPlaceholder(avatarId: Int?, size: Dp) {
    if (avatarId != null) {
        AvatarImage(avatarId = avatarId, size = size)
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun ChatComposer(
    value: String,
    onChange: (String) -> Unit,
    isHost: Boolean,
    sendAsAnnouncement: Boolean,
    onToggleAnnounce: (Boolean) -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isHost) {
            FilterChip(
                selected = sendAsAnnouncement,
                onClick = { onToggleAnnounce(!sendAsAnnouncement) },
                label = { Text(stringResource(Res.string.chat_announce_toggle)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = {
                    Text(
                        if (sendAsAnnouncement) stringResource(Res.string.chat_announce_hint)
                        else stringResource(Res.string.chat_input_hint),
                    )
                },
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onSend,
                enabled = value.isNotBlank(),
                colors = if (sendAsAnnouncement) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(stringResource(Res.string.chat_send))
            }
        }
    }
}
