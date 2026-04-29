package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.trivia.TriviaChoice
import com.xergioalex.kmppttdynamics.trivia.TriviaQuestion
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_back
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.trivia_add_question
import kmppttdynamics.composeapp.generated.resources.trivia_choice_correct
import kmppttdynamics.composeapp.generated.resources.trivia_choice_label
import kmppttdynamics.composeapp.generated.resources.trivia_delete_question
import kmppttdynamics.composeapp.generated.resources.trivia_delete_quiz
import kmppttdynamics.composeapp.generated.resources.trivia_edit_question
import kmppttdynamics.composeapp.generated.resources.trivia_open_lobby
import kmppttdynamics.composeapp.generated.resources.trivia_open_lobby_helper
import kmppttdynamics.composeapp.generated.resources.trivia_question_n
import kmppttdynamics.composeapp.generated.resources.trivia_question_prompt
import kmppttdynamics.composeapp.generated.resources.trivia_question_seconds
import kmppttdynamics.composeapp.generated.resources.trivia_save_question
import kmppttdynamics.composeapp.generated.resources.trivia_setup_helper
import kmppttdynamics.composeapp.generated.resources.trivia_setup_no_questions
import kmppttdynamics.composeapp.generated.resources.trivia_setup_title
import org.jetbrains.compose.resources.stringResource

/**
 * Host-side editor for a trivia quiz in `draft` state. Each question is
 * a card with the prompt, four colored choice rows, a per-question time
 * slider and a "delete" action. Editing happens through a dialog so
 * the list stays scannable when the host is reordering ten questions.
 *
 * Validation for "Open lobby" — the button stays disabled unless every
 * question has:
 *   - a non-blank prompt
 *   - exactly four non-blank choices
 *   - exactly one choice marked `is_correct`
 *
 * Choices come from [choicesByQuestion] keyed by question id; the
 * snapshot from realtime always has them sorted by `position`.
 */
@Composable
fun HostSetupScreen(
    quiz: TriviaQuiz,
    questions: List<TriviaQuestion>,
    choicesByQuestion: Map<String, List<TriviaChoice>>,
    isWorking: Boolean,
    onAddQuestion: (prompt: String, seconds: Int, labels: List<String>, correctIndex: Int) -> Unit,
    onUpdateQuestion: (questionId: String, prompt: String, seconds: Int, labels: List<String>, correctIndex: Int) -> Unit,
    onDeleteQuestion: (questionId: String) -> Unit,
    onOpenLobby: () -> Unit,
    /** Bounce back to the trivia list without changing the quiz. */
    onBack: () -> Unit,
    /** Permanently destroy the quiz (and its questions / choices via CASCADE). */
    onDeleteQuiz: () -> Unit,
) {
    var editorState by remember { mutableStateOf<EditorState?>(null) }

    val readyQuestions = questions.filter { q ->
        val cs = choicesByQuestion[q.id].orEmpty()
        cs.size == 4 && cs.all { it.label.isNotBlank() } && cs.count { it.isCorrect } == 1
    }
    val canOpenLobby = !isWorking && readyQuestions.size == questions.size && questions.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                stringResource(Res.string.trivia_setup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.trivia_setup_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
        }

        // Hoisted so the add-question CTA opens the editor from both
        // the empty-state branch (primary Button) and the end-of-list
        // branch (subtle OutlinedButton). Without this, the empty
        // state had no entry point — only the LazyColumn rendered the
        // button, so brand-new quizzes were unreachable.
        val openNewQuestionEditor = {
            editorState = EditorState(
                existingId = null,
                initialPrompt = "",
                initialSeconds = quiz.defaultSecondsPerQuestion,
                initialLabels = listOf("", "", "", ""),
                initialCorrect = 0,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (questions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(Res.string.trivia_setup_no_questions),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = openNewQuestionEditor,
                        enabled = !isWorking,
                    ) { Text(stringResource(Res.string.trivia_add_question)) }
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(questions, key = { _, q -> q.id }) { index, q ->
                        QuestionSummaryCard(
                            position = index,
                            question = q,
                            choices = choicesByQuestion[q.id].orEmpty(),
                            isWorking = isWorking,
                            onEdit = {
                                editorState = EditorState(
                                    existingId = q.id,
                                    initialPrompt = q.prompt,
                                    initialSeconds = q.secondsToAnswer,
                                    initialLabels = (choicesByQuestion[q.id].orEmpty()
                                        .sortedBy { it.position }
                                        .map { it.label } + List(4) { "" }).take(4),
                                    initialCorrect = choicesByQuestion[q.id].orEmpty()
                                        .firstOrNull { it.isCorrect }?.position ?: 0,
                                )
                            },
                            onDelete = { onDeleteQuestion(q.id) },
                        )
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = openNewQuestionEditor,
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(Res.string.trivia_add_question)) }
                    }
                }
            }
        }

        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        if (questions.isEmpty() || !canOpenLobby) {
            Text(
                stringResource(Res.string.trivia_open_lobby_helper),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isWorking,
            ) { Text(stringResource(Res.string.action_back)) }
            TextButton(
                onClick = onDeleteQuiz,
                enabled = !isWorking,
            ) {
                Text(
                    stringResource(Res.string.trivia_delete_quiz),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onOpenLobby,
                enabled = canOpenLobby,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(Res.string.trivia_open_lobby)) }
        }
        Spacer(Modifier.height(4.dp))
    }

    editorState?.let { state ->
        QuestionEditorDialog(
            state = state,
            onCancel = { editorState = null },
            onSave = { prompt, secs, labels, correct ->
                editorState = null
                if (state.existingId == null) {
                    onAddQuestion(prompt, secs, labels, correct)
                } else {
                    onUpdateQuestion(state.existingId, prompt, secs, labels, correct)
                }
            },
        )
    }
}

/**
 * Compact card shown for each existing question in the setup list.
 * Renders the four choices as colored chips so the host can spot the
 * correct one at a glance (it's the chip with a check mark).
 */
@Composable
private fun QuestionSummaryCard(
    position: Int,
    question: TriviaQuestion,
    choices: List<TriviaChoice>,
    isWorking: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val ordered = choices.sortedBy { it.position }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.trivia_question_n, position + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(Res.string.trivia_question_seconds, question.secondsToAnswer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                question.prompt.ifBlank { "—" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ordered.take(4).forEachIndexed { idx, choice ->
                    ChoiceChip(
                        index = idx,
                        label = choice.label.ifBlank { "—" },
                        isCorrect = choice.isCorrect,
                    )
                }
                // Show empty placeholder rows so a half-filled question
                // is obvious in the list.
                repeat((4 - ordered.size).coerceAtLeast(0)) { idx ->
                    ChoiceChip(
                        index = ordered.size + idx,
                        label = "—",
                        isCorrect = false,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onEdit, enabled = !isWorking) {
                    Text(stringResource(Res.string.trivia_edit_question))
                }
                TextButton(onClick = onDelete, enabled = !isWorking) {
                    Text(
                        stringResource(Res.string.trivia_delete_question),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(index: Int, label: String, isCorrect: Boolean) {
    val bg = TriviaPalette.backgrounds[index]
    val fg = TriviaPalette.foregrounds[index]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            TriviaPalette.symbols[index],
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (isCorrect) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2713", color = bg, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Local mutable state held by the question editor dialog. Lifted out
 * so we can drive the dialog from the screen-level "edit" / "add"
 * actions with a single composable.
 */
private data class EditorState(
    val existingId: String?,
    val initialPrompt: String,
    val initialSeconds: Int,
    val initialLabels: List<String>,
    val initialCorrect: Int,
)

@Composable
private fun QuestionEditorDialog(
    state: EditorState,
    onCancel: () -> Unit,
    onSave: (prompt: String, seconds: Int, labels: List<String>, correctIndex: Int) -> Unit,
) {
    var prompt by remember { mutableStateOf(state.initialPrompt) }
    var seconds by remember { mutableIntStateOf(state.initialSeconds) }
    val labels = remember {
        mutableStateListOf<String>().also { it.addAll(state.initialLabels.take(4)) }
    }
    var correctIndex by remember { mutableIntStateOf(state.initialCorrect.coerceIn(0, 3)) }

    val canSave = prompt.isNotBlank() && labels.all { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                if (state.existingId == null) {
                    stringResource(Res.string.trivia_add_question)
                } else {
                    stringResource(Res.string.trivia_edit_question)
                },
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(Res.string.trivia_question_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.trivia_question_seconds, seconds),
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = seconds.toFloat(),
                    onValueChange = { seconds = it.toInt().coerceIn(5, 60) },
                    valueRange = 5f..60f,
                    steps = 10,
                )
                Spacer(Modifier.height(8.dp))
                labels.forEachIndexed { idx, _ ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = correctIndex == idx,
                            onClick = { correctIndex = idx },
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(TriviaPalette.backgrounds[idx]),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                TriviaPalette.symbols[idx],
                                color = TriviaPalette.foregrounds[idx],
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        OutlinedTextField(
                            value = labels[idx],
                            onValueChange = { labels[idx] = it },
                            label = { Text(stringResource(Res.string.trivia_choice_label, idx + 1)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    stringResource(Res.string.trivia_choice_correct),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(prompt, seconds, labels.toList(), correctIndex) },
                enabled = canSave,
            ) { Text(stringResource(Res.string.trivia_save_question)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
