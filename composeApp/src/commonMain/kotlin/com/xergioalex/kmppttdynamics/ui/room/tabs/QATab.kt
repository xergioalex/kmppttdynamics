package com.xergioalex.kmppttdynamics.ui.room.tabs

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.domain.Question
import com.xergioalex.kmppttdynamics.domain.QuestionStatus
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.qa_ask
import kmppttdynamics.composeapp.generated.resources.qa_ask_hint
import kmppttdynamics.composeapp.generated.resources.qa_empty
import kmppttdynamics.composeapp.generated.resources.qa_hide
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
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var myVotes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var draft by remember { mutableStateOf("") }
    val isHost = me.role == ParticipantRole.HOST

    LaunchedEffect(meetupId) {
        container.questions.observe(meetupId)
            .catch { /* later */ }
            .collect { list ->
                questions = list
                myVotes = runCatching { container.questions.listMyVotes(meetupId, me.id) }
                    .getOrDefault(emptySet())
            }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        // Ask form
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(280) },
            placeholder = { Text(stringResource(Res.string.qa_ask_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
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
            if (questions.isEmpty()) {
                Text(
                    stringResource(Res.string.qa_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(questions, key = { it.id }) { q ->
                        QuestionRow(
                            q = q,
                            iVoted = q.id in myVotes,
                            isHost = isHost,
                            onToggleVote = {
                                scope.launch {
                                    runCatching {
                                        if (q.id in myVotes) container.questions.unvote(q.id, me.id)
                                        else container.questions.upvote(q.id, me.id)
                                    }
                                    // Refresh local votes immediately for snappier UX —
                                    // realtime will reconcile the count.
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
            Column(
                modifier = Modifier.size(width = 56.dp, height = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = onToggleVote, enabled = !isAnswered) {
                    Text(
                        if (iVoted) "▲" else "△",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (iVoted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    q.upvotesCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(q.question, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isAnswered) {
                            stringResource(Res.string.qa_status_answered)
                        } else {
                            stringResource(Res.string.qa_status_open)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isAnswered) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(Res.string.qa_upvotes, q.upvotesCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isHost && !isAnswered) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onMarkAnswered) {
                            Text(stringResource(Res.string.qa_mark_answered))
                        }
                        TextButton(onClick = onHide) {
                            Text(stringResource(Res.string.qa_hide))
                        }
                    }
                }
            }
        }
    }
}
