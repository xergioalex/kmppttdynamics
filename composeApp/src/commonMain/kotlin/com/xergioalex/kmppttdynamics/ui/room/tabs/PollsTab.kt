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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.domain.Poll
import com.xergioalex.kmppttdynamics.domain.PollOption
import com.xergioalex.kmppttdynamics.domain.PollStatus
import com.xergioalex.kmppttdynamics.domain.PollVote
import com.xergioalex.kmppttdynamics.polls.PollBoard
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.polls_add_option
import kmppttdynamics.composeapp.generated.resources.polls_anonymous
import kmppttdynamics.composeapp.generated.resources.polls_close
import kmppttdynamics.composeapp.generated.resources.polls_create
import kmppttdynamics.composeapp.generated.resources.polls_empty
import kmppttdynamics.composeapp.generated.resources.polls_field_option
import kmppttdynamics.composeapp.generated.resources.polls_field_question
import kmppttdynamics.composeapp.generated.resources.polls_publish
import kmppttdynamics.composeapp.generated.resources.polls_remove_option
import kmppttdynamics.composeapp.generated.resources.polls_status_closed
import kmppttdynamics.composeapp.generated.resources.polls_status_draft
import kmppttdynamics.composeapp.generated.resources.polls_status_open
import kmppttdynamics.composeapp.generated.resources.polls_total_votes
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun PollsTab(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf(PollBoard(emptyList(), emptyMap(), emptyMap())) }
    var showCreate by remember { mutableStateOf(false) }
    val isHost = me.role == ParticipantRole.HOST

    LaunchedEffect(meetupId) {
        container.polls.observeBoard(meetupId)
            .catch { /* later */ }
            .collect { board = it }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        if (isHost) {
            Button(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.polls_create)) }
            Spacer(Modifier.height(12.dp))
        }
        if (board.polls.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.polls_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(board.polls, key = { it.id }) { poll ->
                    PollCard(
                        poll = poll,
                        options = board.options[poll.id].orEmpty(),
                        votes = board.votes[poll.id].orEmpty(),
                        myParticipantId = me.id,
                        isHost = isHost,
                        onVote = { optionId ->
                            scope.launch {
                                runCatching { container.polls.vote(poll.id, optionId, me.id) }
                            }
                        },
                        onClose = {
                            scope.launch { runCatching { container.polls.close(poll.id) } }
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreatePollDialog(
            onDismiss = { showCreate = false },
            onCreate = { question, options, anon ->
                scope.launch {
                    runCatching {
                        container.polls.create(
                            meetupId = meetupId,
                            hostParticipantId = me.id,
                            question = question,
                            options = options,
                            isAnonymous = anon,
                        )
                    }
                }
                showCreate = false
            },
        )
    }
}

@Composable
private fun PollCard(
    poll: Poll,
    options: List<PollOption>,
    votes: List<PollVote>,
    myParticipantId: String,
    isHost: Boolean,
    onVote: (optionId: String) -> Unit,
    onClose: () -> Unit,
) {
    val total = votes.size
    val countsByOption = remember(votes) { votes.groupingBy { it.optionId }.eachCount() }
    val myVote = remember(votes, myParticipantId) {
        votes.firstOrNull { it.participantId == myParticipantId }?.optionId
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    poll.question,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    statusLabel(poll.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(10.dp))
            options.forEach { option ->
                val count = countsByOption[option.id] ?: 0
                val pct = if (total > 0) count.toFloat() / total else 0f
                PollOptionRow(
                    text = option.text,
                    count = count,
                    pct = pct,
                    selected = option.id == myVote,
                    canVote = poll.status.canVote,
                    onClick = { onVote(option.id) },
                )
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.polls_total_votes, total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (isHost && poll.status == PollStatus.OPEN) {
                    TextButton(onClick = onClose) {
                        Text(stringResource(Res.string.polls_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun PollOptionRow(
    text: String,
    count: Int,
    pct: Float,
    selected: Boolean,
    canVote: Boolean,
    onClick: () -> Unit,
) {
    val borderTint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        // Filled progress bar.
        Box(
            modifier = Modifier
                .fillMaxWidth(pct.coerceIn(0f, 1f))
                .height(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = borderTint,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canVote) {
            // Stretched-but-invisible click target on top of the bar.
            TextButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(36.dp),
            ) { Text("") }
        }
    }
}

@Composable
private fun statusLabel(status: PollStatus): String = when (status) {
    PollStatus.DRAFT -> stringResource(Res.string.polls_status_draft)
    PollStatus.OPEN -> stringResource(Res.string.polls_status_open)
    PollStatus.CLOSED -> stringResource(Res.string.polls_status_closed)
    PollStatus.ARCHIVED -> status.name.lowercase()
}

@Composable
private fun CreatePollDialog(
    onDismiss: () -> Unit,
    onCreate: (question: String, options: List<String>, anon: Boolean) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var anon by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.polls_create)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(Res.string.polls_field_question)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                options.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { v ->
                                options = options.toMutableList().also { it[index] = v }
                            },
                            label = { Text(stringResource(Res.string.polls_field_option, index + 1)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (options.size > 2) {
                            TextButton(onClick = {
                                options = options.toMutableList().also { it.removeAt(index) }
                            }) { Text(stringResource(Res.string.polls_remove_option)) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (options.size < 6) {
                    TextButton(onClick = { options = options + "" }) {
                        Text(stringResource(Res.string.polls_add_option))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = anon, onCheckedChange = { anon = it })
                    Spacer(Modifier.height(0.dp))
                    Text(
                        stringResource(Res.string.polls_anonymous),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = options.map { it.trim() }.filter { it.isNotEmpty() }
                    if (question.isNotBlank() && cleaned.size >= 2) {
                        onCreate(question, cleaned, anon)
                    }
                },
                enabled = question.isNotBlank() &&
                    options.count { it.trim().isNotEmpty() } >= 2,
            ) { Text(stringResource(Res.string.polls_publish)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
