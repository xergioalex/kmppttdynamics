package com.xergioalex.kmppttdynamics.ui.room.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.xergioalex.kmppttdynamics.ui.room.tabs.trivia.LobbyScreen
import com.xergioalex.kmppttdynamics.ui.room.tabs.trivia.QuestionScreen
import kmppttdynamics.composeapp.generated.resources.Res
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
 * Loads the latest active quiz for the meetup via
 * [com.xergioalex.kmppttdynamics.trivia.TriviaRepository.observeBoard] and routes to one of five
 * sub-screens based on `quiz.status`. The room screen (header / tab
 * strip / leave button) is rendered by `RoomScreen` — this composable
 * only owns the body of the trivia tab.
 *
 * The host's device drives every state transition; non-host devices
 * are pure observers. WHERE-guarded UPDATEs in the repository keep
 * concurrent host clicks idempotent.
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
    val isHost = me.role == ParticipantRole.HOST
    val myClientId = me.clientId ?: container.settings.installClientId()

    LaunchedEffect(meetupId) {
        container.trivia.observeBoard(meetupId)
            .catch { board = TriviaBoard(emptyList(), emptyMap(), emptyMap()) }
            .collect { board = it }
    }

    /**
     * Run a suspending host action exactly once at a time. While
     * [working] is non-null every action button greys out — this
     * coroutine is the safety net that catches race conditions
     * (rapid double-tap, network retry, etc.) and surfaces failures.
     */
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

    val quiz: TriviaQuiz? = board?.active

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

                quiz == null -> EmptyState(
                    isHost = isHost,
                    onCreate = { showCreate = true },
                )

                else -> {
                    val questions = board?.questionsByQuiz?.get(quiz.id).orEmpty()
                    val choices = board?.choicesByQuestion ?: emptyMap()
                    when (quiz.status) {
                        TriviaStatus.DRAFT -> if (isHost) {
                            HostSetupScreen(
                                quiz = quiz,
                                questions = questions,
                                choicesByQuestion = choices,
                                isWorking = working != null,
                                onAddQuestion = { prompt, secs, labels, correctIdx ->
                                    runAction("add-question") {
                                        val pos = questions.size
                                        val q = container.trivia.addQuestion(
                                            quizId = quiz.id,
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
                                        container.trivia.openLobby(quiz.id)
                                    }
                                },
                                onDiscardQuiz = {
                                    runAction("discard-quiz") {
                                        container.trivia.deleteQuiz(quiz.id)
                                    }
                                },
                            )
                        } else {
                            CenteredHint(stringResource(Res.string.trivia_empty_guest))
                        }

                        TriviaStatus.LOBBY -> LobbyScreen(
                            quiz = quiz,
                            questions = questions,
                            participantsById = participantsById,
                            usersByClientId = usersByClientId,
                            isHost = isHost,
                            isWorking = working != null,
                            onStart = {
                                runAction("start") { container.trivia.start(quiz.id) }
                            },
                            onBackToSetup = {
                                runAction("back-to-setup") {
                                    container.trivia.returnToDraft(quiz.id)
                                }
                            },
                        )

                        TriviaStatus.IN_PROGRESS -> QuestionScreen(
                            quiz = quiz,
                            questions = questions,
                            choicesByQuestion = choices,
                            container = container,
                            me = me,
                            myClientId = myClientId,
                            isHost = isHost,
                            isWorking = working != null,
                            onlineCount = participantsById.values.count { it.isOnline },
                            onAdvance = { expectedIndex ->
                                runAction("advance") {
                                    container.trivia.advanceQuestion(
                                        quizId = quiz.id,
                                        expectedIndex = expectedIndex,
                                        totalQuestions = questions.size,
                                    )
                                }
                            },
                        )

                        TriviaStatus.CALCULATING -> CalculatingScreen(
                            quiz = quiz,
                            onFinish = {
                                runAction("finish-calculating") {
                                    container.trivia.finishCalculating(quiz.id)
                                }
                            },
                        )

                        TriviaStatus.FINISHED -> LeaderboardScreen(
                            quiz = quiz,
                            container = container,
                            isHost = isHost,
                            isWorking = working != null,
                            onPlayAgain = {
                                runAction("reset") { container.trivia.reset(quiz.id) }
                            },
                            onNewRound = {
                                runAction("new-round") {
                                    val title = "Round ${board?.quizzes?.size?.plus(1) ?: 1}"
                                    container.trivia.createQuiz(
                                        meetupId = meetupId,
                                        title = title,
                                        createdByClientId = myClientId,
                                    )
                                }
                            },
                        )
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
                    container.trivia.createQuiz(
                        meetupId = meetupId,
                        title = title,
                        createdByClientId = myClientId,
                    )
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
private fun CenteredHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
