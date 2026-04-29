package com.xergioalex.kmppttdynamics.ui.room.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.xergioalex.kmppttdynamics.ui.components.IconThumbUp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.domain.Question
import com.xergioalex.kmppttdynamics.domain.QuestionStatus
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.qa_ask
import kmppttdynamics.composeapp.generated.resources.qa_ask_hint
import kmppttdynamics.composeapp.generated.resources.qa_empty
import kmppttdynamics.composeapp.generated.resources.qa_hide
import kmppttdynamics.composeapp.generated.resources.qa_loading
import kmppttdynamics.composeapp.generated.resources.qa_mark_answered
import kmppttdynamics.composeapp.generated.resources.qa_status_answered
import kmppttdynamics.composeapp.generated.resources.qa_status_open
import kmppttdynamics.composeapp.generated.resources.qa_upvotes
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun QATab(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    participantsById: Map<String, MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var questions by remember { mutableStateOf<List<Question>?>(null) }
    var myVotes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var draft by remember { mutableStateOf("") }
    val isHost = me.role == ParticipantRole.HOST

    LaunchedEffect(meetupId) {
        container.questions.observe(meetupId)
            .catch { questions = emptyList() }
            .collect { list ->
                questions = list
                myVotes = runCatching { container.questions.listMyVotes(meetupId, me.id) }
                    .getOrDefault(emptySet())
            }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(280) },
            placeholder = { Text(stringResource(Res.string.qa_ask_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    val text = draft
                    draft = ""
                    scope.launch {
                        runCatching { container.questions.ask(meetupId, me.id, text) }
                    }
                },
                enabled = draft.isNotBlank(),
            ) { Text(stringResource(Res.string.qa_ask)) }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                questions == null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.qa_loading),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                questions!!.isEmpty() -> Text(
                    stringResource(Res.string.qa_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(questions!!, key = { it.id }) { q ->
                        val asker = q.participantId?.let { participantsById[it] }
                        val askerAvatar = asker?.clientId?.let { usersByClientId[it]?.avatarId }
                        QuestionRow(
                            q = q,
                            askerName = asker?.displayName,
                            askerAvatar = askerAvatar,
                            iVoted = q.id in myVotes,
                            isHost = isHost,
                            onToggleVote = {
                                scope.launch {
                                    runCatching {
                                        if (q.id in myVotes) container.questions.unvote(q.id, me.id)
                                        else container.questions.upvote(q.id, me.id)
                                    }
                                    myVotes = if (q.id in myVotes) myVotes - q.id else myVotes + q.id
                                }
                            },
                            onMarkAnswered = {
                                scope.launch { runCatching { container.questions.markAnswered(q.id) } }
                            },
                            onHide = {
                                scope.launch { runCatching { container.questions.hide(q.id) } }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionRow(
    q: Question,
    askerName: String?,
    askerAvatar: Int?,
    iVoted: Boolean,
    isHost: Boolean,
    onToggleVote: () -> Unit,
    onMarkAnswered: () -> Unit,
    onHide: () -> Unit,
) {
    val isAnswered = q.status == QuestionStatus.ANSWERED
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            VotePill(
                count = q.upvotesCount,
                voted = iVoted,
                enabled = !isAnswered,
                onClick = onToggleVote,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarOrPlaceholder(avatarId = askerAvatar, size = 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = askerName ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusChip(isAnswered)
                }
                Spacer(Modifier.height(6.dp))
                Text(q.question, style = MaterialTheme.typography.bodyLarge)
                if (isHost && !isAnswered) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onMarkAnswered, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(stringResource(Res.string.qa_mark_answered))
                        }
                        TextButton(onClick = onHide, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(stringResource(Res.string.qa_hide))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VotePill(
    count: Int,
    voted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        voted -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        voted -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(width = 56.dp, height = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .let { if (enabled) it.clickable(onClick = onClick) else it },
    ) {
        IconThumbUp(
            tint = fg,
            filled = voted,
            size = 20.dp,
        )
        Text(
            stringResource(Res.string.qa_upvotes, count),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusChip(answered: Boolean) {
    val (label, fg, bg) = if (answered) {
        Triple(
            stringResource(Res.string.qa_status_answered),
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
        )
    } else {
        Triple(
            stringResource(Res.string.qa_status_open),
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
