package com.xergioalex.kmppttdynamics.ui.room.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.xergioalex.kmppttdynamics.domain.AppUser
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.domain.ParticipantRole
import com.xergioalex.kmppttdynamics.trivia.TriviaBoard
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import com.xergioalex.kmppttdynamics.trivia.TriviaStatus
import com.xergioalex.kmppttdynamics.ui.room.tabs.trivia.CalculatingScreen
import com.xergioalex.kmppttdynamics.ui.room.tabs.trivia.HostSetupScreen
import com.xergioalex.kmppttdynamics.ui.room.tabs.trivia.LeaderboardScreen
import com.xergioalex.kmppttdynamics.ui.room.tabs.trivia.QuestionScreen
import com.xergioalex.kmppttdynamics.ui.room.tabs.trivia.TriviaCard
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_back
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.trivia_action_failed
import kmppttdynamics.composeapp.generated.resources.trivia_create
import kmppttdynamics.composeapp.generated.resources.trivia_default_title
import kmppttdynamics.composeapp.generated.resources.trivia_empty_guest
import kmppttdynamics.composeapp.generated.resources.trivia_empty_host
import kmppttdynamics.composeapp.generated.resources.trivia_field_title
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Kahoot-style trivia tab.
 *
 * Multiple quizzes can coexist for one meetup; the tab is therefore
 * structured around a **list of [TriviaCard]s** (one per quiz, sorted
 * by `created_at DESC`). Two routes pull a single quiz out of the
 * list to take over the screen:
 *
 *  - **Live takeover** — when any quiz is `IN_PROGRESS` or
 *    `CALCULATING`, the room is in active gameplay; the in-progress /
 *    suspense screens render full-bleed for *every* connected device,
 *    enrolled or not.
 *  - **Local routes** (host only):
 *      - `editingQuizId != null` → [HostSetupScreen] for that quiz.
 *      - `viewingLeaderboardQuizId != null` → [LeaderboardScreen] for
 *        a finished quiz (the podium needs the full screen).
 *
 * Routing precedence: live takeover > local route > list view. So if
 * the host is editing a draft and a *different* quiz transitions to
 * in_progress (because, say, a co-host hit Start), the live takeover
 * wins and the host returns to the editor when the round ends.
 */
@Composable
fun TriviaTab(
    container: AppContainer,
    meetupId: String,
    me: MeetupParticipant,
    participantsById: Map<String, MeetupParticipant>,
    usersByClientId: Map<String, AppUser>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<TriviaBoard?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var editingQuizId by remember { mutableStateOf<String?>(null) }
    var viewingLeaderboardQuizId by remember { mutableStateOf<String?>(null) }
    val isHost = me.role == ParticipantRole.HOST
    val myClientId = me.clientId ?: container.settings.installClientId()

    LaunchedEffect(meetupId) {
        container.trivia.observeBoard(meetupId)
            .catch { board = TriviaBoard(emptyList(), emptyMap(), emptyMap()) }
            .collect { board = it }
    }

    fun runAction(label: String, block: suspend () -> Unit) {
        if (working != null) return
        actionError = null
        working = label
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                println("TriviaTab[$label] failed: ${t::class.simpleName}: ${t.message}")
                actionError = t.message ?: t::class.simpleName ?: "?"
            } finally {
                working = null
            }
        }
    }

    val live = board?.live
    // Resolve local-route targets against the live board so a stale
    // editingQuizId pointing at a deleted quiz doesn't strand the UI.
    val editingQuiz = editingQuizId?.let { id -> board?.quizzes?.firstOrNull { it.id == id } }
    val leaderboardQuiz = viewingLeaderboardQuizId?.let { id ->
        board?.quizzes?.firstOrNull { it.id == id }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        actionError?.let { msg ->
            Text(
                stringResource(Res.string.trivia_action_failed, msg),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                board == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                // ---- 1) Live takeover -------------------------------
                live != null -> when (live.status) {
                    TriviaStatus.IN_PROGRESS -> QuestionScreen(
                        quiz = live,
                        questions = board?.questionsByQuiz?.get(live.id).orEmpty(),
                        choicesByQuestion = board?.choicesByQuestion ?: emptyMap(),
                        container = container,
                        me = me,
                        myClientId = myClientId,
                        isHost = isHost,
                        isWorking = working != null,
                        onlineCount = participantsById.values.count { it.isOnline },
                        canAnswer = board?.entriesByQuiz?.get(live.id)
                            ?.any { it.clientId == myClientId } == true,
                        onAdvance = { expectedIndex ->
                            runAction("advance") {
                                container.trivia.advanceQuestion(
                                    quizId = live.id,
                                    expectedIndex = expectedIndex,
                                    totalQuestions = board?.questionsByQuiz
                                        ?.get(live.id)?.size ?: 0,
                                )
                            }
                        },
                    )
                    TriviaStatus.CALCULATING -> CalculatingScreen(
                        quiz = live,
                        onFinish = {
                            runAction("finish-calculating") {
                                container.trivia.finishCalculating(live.id)
                            }
                        },
                    )
                    else -> Unit
                }

                // ---- 2) Local route: editing a draft ---------------
                editingQuiz != null && editingQuiz.status == TriviaStatus.DRAFT && isHost -> {
                    HostSetupScreen(
                        quiz = editingQuiz,
                        questions = board?.questionsByQuiz?.get(editingQuiz.id).orEmpty(),
                        choicesByQuestion = board?.choicesByQuestion ?: emptyMap(),
                        isWorking = working != null,
                        onAddQuestion = { prompt, secs, labels, correctIdx ->
                            runAction("add-question") {
                                val pos = board?.questionsByQuiz
                                    ?.get(editingQuiz.id)?.size ?: 0
                                val q = container.trivia.addQuestion(
                                    quizId = editingQuiz.id,
                                    position = pos,
                                    prompt = prompt,
                                    secondsToAnswer = secs,
                                )
                                container.trivia.replaceChoices(q.id, labels, correctIdx)
                            }
                        },
                        onUpdateQuestion = { questionId, prompt, secs, labels, correctIdx ->
                            runAction("update-question") {
                                container.trivia.updateQuestion(questionId, prompt, secs)
                                container.trivia.replaceChoices(questionId, labels, correctIdx)
                            }
                        },
                        onDeleteQuestion = { questionId ->
                            runAction("delete-question") {
                                container.trivia.deleteQuestion(questionId)
                            }
                        },
                        onOpenLobby = {
                            runAction("open-lobby") {
                                container.trivia.openLobby(editingQuiz.id)
                            }
                            // Bounce back to the list — the LOBBY card
                            // will surface the participant join + start
                            // controls.
                            editingQuizId = null
                        },
                        onBack = { editingQuizId = null },
                        onDeleteQuiz = {
                            runAction("delete-quiz") {
                                container.trivia.deleteQuiz(editingQuiz.id)
                            }
                            editingQuizId = null
                        },
                    )
                }

                // ---- 3) Local route: viewing a finished leaderboard
                leaderboardQuiz != null && leaderboardQuiz.status == TriviaStatus.FINISHED -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(
                            onClick = { viewingLeaderboardQuizId = null },
                        ) { Text(stringResource(Res.string.action_back)) }
                        LeaderboardScreen(
                            quiz = leaderboardQuiz,
                            container = container,
                            isHost = isHost,
                            isWorking = working != null,
                            onPlayAgain = {
                                runAction("reset") {
                                    container.trivia.reset(leaderboardQuiz.id)
                                }
                                viewingLeaderboardQuizId = null
                            },
                            onNewRound = {
                                viewingLeaderboardQuizId = null
                                showCreate = true
                            },
                        )
                    }
                }

                // ---- 4) Default: list view of all quizzes -----------
                else -> {
                    val quizzes = board?.quizzes.orEmpty()
                    if (quizzes.isEmpty()) {
                        EmptyState(
                            isHost = isHost,
                            onCreate = { showCreate = true },
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (isHost) {
                                item {
                                    Button(
                                        onClick = { showCreate = true },
                                        enabled = working == null,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(stringResource(Res.string.trivia_create)) }
                                }
                            }
                            items(quizzes, key = { it.id }) { quiz ->
                                TriviaCard(
                                    quiz = quiz,
                                    questions = board?.questionsByQuiz
                                        ?.get(quiz.id).orEmpty(),
                                    choicesByQuestion = board?.choicesByQuestion
                                        ?: emptyMap(),
                                    entries = board?.entriesByQuiz
                                        ?.get(quiz.id).orEmpty(),
                                    me = me,
                                    myClientId = myClientId,
                                    isHost = isHost,
                                    isWorking = working != null,
                                    participantsById = participantsById,
                                    usersByClientId = usersByClientId,
                                    onEdit = { editingQuizId = quiz.id },
                                    onDelete = {
                                        runAction("delete-quiz") {
                                            container.trivia.deleteQuiz(quiz.id)
                                        }
                                    },
                                    onOpenLobby = {
                                        runAction("open-lobby") {
                                            container.trivia.openLobby(quiz.id)
                                        }
                                    },
                                    onReturnToDraft = {
                                        runAction("return-to-draft") {
                                            container.trivia.returnToDraft(quiz.id)
                                        }
                                    },
                                    onEnter = {
                                        runAction("enter") {
                                            container.trivia.enter(
                                                quizId = quiz.id,
                                                participantId = me.id,
                                                clientId = myClientId,
                                            )
                                        }
                                    },
                                    onEnrollAll = {
                                        runAction("enroll-all") {
                                            container.trivia.enrollAllParticipants(
                                                quizId = quiz.id,
                                                meetupId = meetupId,
                                            )
                                        }
                                    },
                                    onStart = {
                                        runAction("start") {
                                            container.trivia.start(quiz.id)
                                        }
                                    },
                                    onViewLeaderboard = {
                                        viewingLeaderboardQuizId = quiz.id
                                    },
                                    onPlayAgain = {
                                        runAction("reset") {
                                            container.trivia.reset(quiz.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateTriviaDialog(
            defaultTitle = stringResource(Res.string.trivia_default_title),
            onDismiss = { showCreate = false },
            onCreate = { title ->
                showCreate = false
                runAction("create") {
                    val created = container.trivia.createQuiz(
                        meetupId = meetupId,
                        title = title,
                        createdByClientId = myClientId,
                    )
                    // Auto-route the host into the editor for the
                    // freshly-created quiz so the next thing they see
                    // is the empty-state "Add question" CTA.
                    editingQuizId = created.id
                }
            },
        )
    }
}

@Composable
private fun EmptyState(isHost: Boolean, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (isHost) {
                stringResource(Res.string.trivia_empty_host)
            } else {
                stringResource(Res.string.trivia_empty_guest)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        if (isHost) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.trivia_create))
            }
        }
    }
}

@Composable
private fun CreateTriviaDialog(
    defaultTitle: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by remember { mutableStateOf(defaultTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.trivia_create)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(Res.string.trivia_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onCreate(title) },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(Res.string.trivia_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
