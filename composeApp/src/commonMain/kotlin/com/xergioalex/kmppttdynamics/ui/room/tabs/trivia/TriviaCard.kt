package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.trivia.TriviaChoice
import com.xergioalex.kmppttdynamics.trivia.TriviaEntry
import com.xergioalex.kmppttdynamics.trivia.TriviaQuestion
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import com.xergioalex.kmppttdynamics.trivia.TriviaStatus
import com.xergioalex.kmppttdynamics.ui.components.AvatarImage
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.trivia_delete_quiz
import kmppttdynamics.composeapp.generated.resources.trivia_edit_questions
import kmppttdynamics.composeapp.generated.resources.trivia_enroll_all
import kmppttdynamics.composeapp.generated.resources.trivia_enter
import kmppttdynamics.composeapp.generated.resources.trivia_entered
import kmppttdynamics.composeapp.generated.resources.trivia_entries_count
import kmppttdynamics.composeapp.generated.resources.trivia_host_configuring
import kmppttdynamics.composeapp.generated.resources.trivia_lobby_start
import kmppttdynamics.composeapp.generated.resources.trivia_no_entries
import kmppttdynamics.composeapp.generated.resources.trivia_no_questions_yet
import kmppttdynamics.composeapp.generated.resources.trivia_open_lobby
import kmppttdynamics.composeapp.generated.resources.trivia_play_again
import kmppttdynamics.composeapp.generated.resources.trivia_questions_count
import kmppttdynamics.composeapp.generated.resources.trivia_status_draft
import kmppttdynamics.composeapp.generated.resources.trivia_status_finished
import kmppttdynamics.composeapp.generated.resources.trivia_status_lobby
import kmppttdynamics.composeapp.generated.resources.trivia_view_leaderboard
import org.jetbrains.compose.resources.stringResource

/**
 * Card representation of one [TriviaQuiz] in the trivia tab list.
 *
 * Each status renders a different action set:
 *
 *   * **DRAFT** — shows the question count, host can Edit / Delete /
 *     Open lobby. Participants see "Host is setting up…".
 *   * **LOBBY** — shows the avatar stack of enrolled participants;
 *     anyone in the room can Enter (or sees "You're in"); host sees
 *     Enroll all + Start + Edit + Delete.
 *   * **FINISHED** — shows the entry count + a "View leaderboard"
 *     button. Host gets Play again + Delete.
 *
 * IN_PROGRESS / CALCULATING quizzes are never rendered as cards — the
 * trivia tab takes over the screen with the live UI when any quiz is
 * in those states (see [com.xergioalex.kmppttdynamics.ui.room.tabs.TriviaTab]
 * routing precedence).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriviaCard(
    quiz: TriviaQuiz,
    questions: List<TriviaQuestion>,
    choicesByQuestion: Map<String, List<TriviaChoice>>,
    entries: List<TriviaEntry>,
    me: MeetupParticipant,
    myClientId: String,
    isHost: Boolean,
    isWorking: Boolean,
    participantsById: Map<String, MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenLobby: () -> Unit,
    onReturnToDraft: () -> Unit,
    onEnter: () -> Unit,
    onEnrollAll: () -> Unit,
    onStart: () -> Unit,
    onViewLeaderboard: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    val iAmIn = entries.any { it.clientId == myClientId }
    val readyForLobby = questions.isNotEmpty() && questions.all { q ->
        val cs = choicesByQuestion[q.id].orEmpty()
        cs.size == 4 && cs.all { it.label.isNotBlank() } && cs.count { it.isCorrect } == 1
    }

    fun avatarFor(entry: TriviaEntry): Int? {
        // Prefer the denormalized client_id on the entry; fall back to
        // resolving via the meetup_participants row when older entries
        // (created before the column was wired) carry a null clientId.
        val clientId = entry.clientId
            ?: participantsById[entry.participantId]?.clientId
        return clientId?.let { usersByClientId[it]?.avatarId }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    quiz.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(quiz.status)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (questions.isEmpty()) {
                    stringResource(Res.string.trivia_no_questions_yet)
                } else {
                    stringResource(Res.string.trivia_questions_count, questions.size)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Status-specific body + actions.
            when (quiz.status) {
                TriviaStatus.DRAFT -> DraftActions(
                    isHost = isHost,
                    isWorking = isWorking,
                    canOpenLobby = readyForLobby,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onOpenLobby = onOpenLobby,
                )

                TriviaStatus.LOBBY -> {
                    Spacer(Modifier.height(8.dp))
                    if (entries.isEmpty()) {
                        Text(
                            stringResource(Res.string.trivia_no_entries),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EntryAvatarStack(
                                avatarIds = entries.take(6).mapNotNull { avatarFor(it) },
                                extra = (entries.size - 6).coerceAtLeast(0),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(Res.string.trivia_entries_count, entries.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LobbyActions(
                        isHost = isHost,
                        isWorking = isWorking,
                        iAmIn = iAmIn,
                        canStart = entries.isNotEmpty(),
                        onEnter = onEnter,
                        onEnrollAll = onEnrollAll,
                        onStart = onStart,
                        onReturnToDraft = onReturnToDraft,
                        onDelete = onDelete,
                    )
                }

                TriviaStatus.FINISHED -> FinishedActions(
                    isHost = isHost,
                    isWorking = isWorking,
                    onViewLeaderboard = onViewLeaderboard,
                    onPlayAgain = onPlayAgain,
                    onDelete = onDelete,
                )

                // IN_PROGRESS / CALCULATING shouldn't reach the card
                // path (TriviaTab routes them to a takeover screen),
                // but if they do we render a minimal "live" hint
                // instead of crashing.
                else -> Text(
                    "…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftActions(
    isHost: Boolean,
    isWorking: Boolean,
    canOpenLobby: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenLobby: () -> Unit,
) {
    if (!isHost) {
        Text(
            stringResource(Res.string.trivia_host_configuring),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    Spacer(Modifier.height(10.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(onClick = onEdit, enabled = !isWorking) {
            Text(stringResource(Res.string.trivia_edit_questions))
        }
        Button(onClick = onOpenLobby, enabled = !isWorking && canOpenLobby) {
            Text(stringResource(Res.string.trivia_open_lobby))
        }
        TextButton(onClick = onDelete, enabled = !isWorking) {
            Text(
                stringResource(Res.string.trivia_delete_quiz),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LobbyActions(
    isHost: Boolean,
    isWorking: Boolean,
    iAmIn: Boolean,
    canStart: Boolean,
    onEnter: () -> Unit,
    onEnrollAll: () -> Unit,
    onStart: () -> Unit,
    onReturnToDraft: () -> Unit,
    onDelete: () -> Unit,
) {
    Spacer(Modifier.height(10.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (iAmIn) {
            EnteredChip()
        } else {
            Button(onClick = onEnter, enabled = !isWorking) {
                Text(stringResource(Res.string.trivia_enter))
            }
        }
        if (isHost) {
            OutlinedButton(onClick = onEnrollAll, enabled = !isWorking) {
                Text(stringResource(Res.string.trivia_enroll_all))
            }
            Button(onClick = onStart, enabled = !isWorking && canStart) {
                Text(stringResource(Res.string.trivia_lobby_start))
            }
            OutlinedButton(onClick = onReturnToDraft, enabled = !isWorking) {
                Text(stringResource(Res.string.trivia_edit_questions))
            }
            TextButton(onClick = onDelete, enabled = !isWorking) {
                Text(
                    stringResource(Res.string.trivia_delete_quiz),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishedActions(
    isHost: Boolean,
    isWorking: Boolean,
    onViewLeaderboard: () -> Unit,
    onPlayAgain: () -> Unit,
    onDelete: () -> Unit,
) {
    Spacer(Modifier.height(10.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(onClick = onViewLeaderboard, enabled = !isWorking) {
            Text(stringResource(Res.string.trivia_view_leaderboard))
        }
        if (isHost) {
            OutlinedButton(onClick = onPlayAgain, enabled = !isWorking) {
                Text(stringResource(Res.string.trivia_play_again))
            }
            TextButton(onClick = onDelete, enabled = !isWorking) {
                Text(
                    stringResource(Res.string.trivia_delete_quiz),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EnteredChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(Res.string.trivia_entered),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun StatusPill(status: TriviaStatus) {
    val (label, fg, bg) = when (status) {
        TriviaStatus.DRAFT -> Triple(
            stringResource(Res.string.trivia_status_draft),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
        )
        TriviaStatus.LOBBY -> Triple(
            stringResource(Res.string.trivia_status_lobby),
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        TriviaStatus.FINISHED -> Triple(
            stringResource(Res.string.trivia_status_finished),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
        )
        else -> Triple(
            "—",
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
            label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Stacked avatar bubbles, mirrors the Raffles "EntryAvatarStack".
 * Implemented with `Modifier.offset` plus an explicit total width
 * because Compose's measure pass doesn't expand the parent to fit
 * offset children — without the explicit width the rightmost avatars
 * would be clipped.
 */
@Composable
private fun EntryAvatarStack(avatarIds: List<Int>, extra: Int) {
    if (avatarIds.isEmpty() && extra == 0) return
    val avatarSize = 28.dp
    val ringSize = 32.dp
    val step = 22.dp
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
            ) { AvatarImage(avatarId = id, size = avatarSize) }
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
