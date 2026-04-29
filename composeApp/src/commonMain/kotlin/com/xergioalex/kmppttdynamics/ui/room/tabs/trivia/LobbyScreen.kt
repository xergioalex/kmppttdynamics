package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.trivia.TriviaQuestion
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import com.xergioalex.kmppttdynamics.ui.components.AvatarImage
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.trivia_back_to_setup
import kmppttdynamics.composeapp.generated.resources.trivia_lobby_no_questions
import kmppttdynamics.composeapp.generated.resources.trivia_lobby_start
import kmppttdynamics.composeapp.generated.resources.trivia_lobby_summary
import kmppttdynamics.composeapp.generated.resources.trivia_lobby_title
import org.jetbrains.compose.resources.stringResource

/**
 * "Waiting for players" screen shown after the host opens the lobby
 * and before they tap Start. The avatar grid mirrors the app's
 * existing presence pattern: one circular tile per online participant
 * with the muted palette for offline ones.
 *
 * Only the host sees the Start button. The "Edit questions" action
 * is also host-only — it sends the quiz back to draft so the host
 * can keep tuning before the round begins. (Once the round actually
 * starts, this path is unreachable because `status` flips to
 * `in_progress` and the screen routes to the question UI.)
 */
@Composable
fun LobbyScreen(
    quiz: TriviaQuiz,
    questions: List<TriviaQuestion>,
    participantsById: Map<String, MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
    isHost: Boolean,
    isWorking: Boolean,
    onStart: () -> Unit,
    onBackToSetup: () -> Unit,
) {
    val online = remember(participantsById) {
        participantsById.values.filter { it.isOnline }
            .sortedByDescending { it.joinedAt }
    }
    val totalSeconds = questions.sumOf { it.secondsToAnswer }
    val avgSeconds = if (questions.isEmpty()) 0 else totalSeconds / questions.size

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                quiz.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.trivia_lobby_summary, questions.size, avgSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(Res.string.trivia_lobby_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ParticipantGrid(participants = online, usersByClientId = usersByClientId)
        }

        if (isHost) {
            if (questions.isEmpty()) {
                Text(
                    stringResource(Res.string.trivia_lobby_no_questions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onBackToSetup,
                    enabled = !isWorking,
                ) { Text(stringResource(Res.string.trivia_back_to_setup)) }
                Button(
                    onClick = onStart,
                    enabled = !isWorking && questions.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.trivia_lobby_start)) }
            }
        }
    }
}

/**
 * Centered cluster of avatar circles. Discord/Slack-ish "who is in the
 * room" preview, but without the cap — the lobby usually has fewer
 * than 30 people in practice and we want all of them visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantGrid(
    participants: List<MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
) {
    if (participants.isEmpty()) {
        Text(
            "…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (p in participants) {
            val avatarId = p.clientId?.let { usersByClientId[it]?.avatarId }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarId != null) {
                        AvatarImage(avatarId = avatarId, size = 52.dp)
                    } else {
                        Text(
                            p.displayName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    p.displayName.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
