package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.ui.components.IconCheck
import com.xergioalex.kmppttdynamics.trivia.TriviaChoice
import com.xergioalex.kmppttdynamics.trivia.TriviaQuestion
import com.xergioalex.kmppttdynamics.trivia.TriviaQuestionType
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.action_back
import kmppttdynamics.composeapp.generated.resources.action_cancel
import kmppttdynamics.composeapp.generated.resources.trivia_add_question
import kmppttdynamics.composeapp.generated.resources.trivia_boolean_label_false
import kmppttdynamics.composeapp.generated.resources.trivia_boolean_label_true
import kmppttdynamics.composeapp.generated.resources.trivia_choice_correct
import kmppttdynamics.composeapp.generated.resources.trivia_choice_correct_multiple
import kmppttdynamics.composeapp.generated.resources.trivia_choice_label
import kmppttdynamics.composeapp.generated.resources.trivia_delete_question
import kmppttdynamics.composeapp.generated.resources.trivia_delete_quiz
import kmppttdynamics.composeapp.generated.resources.trivia_edit_question
import kmppttdynamics.composeapp.generated.resources.trivia_numeric_expected
import kmppttdynamics.composeapp.generated.resources.trivia_numeric_tolerance
import kmppttdynamics.composeapp.generated.resources.trivia_numeric_tolerance_helper
import kmppttdynamics.composeapp.generated.resources.trivia_open_lobby
import kmppttdynamics.composeapp.generated.resources.trivia_open_lobby_helper
import kmppttdynamics.composeapp.generated.resources.trivia_question_n
import kmppttdynamics.composeapp.generated.resources.trivia_question_prompt
import kmppttdynamics.composeapp.generated.resources.trivia_question_seconds
import kmppttdynamics.composeapp.generated.resources.trivia_question_type_label
import kmppttdynamics.composeapp.generated.resources.trivia_save_question
import kmppttdynamics.composeapp.generated.resources.trivia_setup_helper
import kmppttdynamics.composeapp.generated.resources.trivia_setup_no_questions
import kmppttdynamics.composeapp.generated.resources.trivia_setup_title
import kmppttdynamics.composeapp.generated.resources.trivia_type_boolean
import kmppttdynamics.composeapp.generated.resources.trivia_type_boolean_desc
import kmppttdynamics.composeapp.generated.resources.trivia_type_multiple
import kmppttdynamics.composeapp.generated.resources.trivia_type_multiple_desc
import kmppttdynamics.composeapp.generated.resources.trivia_type_numeric
import kmppttdynamics.composeapp.generated.resources.trivia_type_numeric_desc
import kmppttdynamics.composeapp.generated.resources.trivia_type_pick_subtitle
import kmppttdynamics.composeapp.generated.resources.trivia_type_pick_title
import kmppttdynamics.composeapp.generated.resources.trivia_type_single
import kmppttdynamics.composeapp.generated.resources.trivia_type_single_desc
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Host-side editor for a trivia quiz in `draft` state.
 *
 * The "Add question" flow is two-stage:
 *  1. **TypePickerDialog** — host picks one of the four
 *     [TriviaQuestionType]s on a 2x2 card grid.
 *  2. **QuestionEditorDialog** — pre-configured for the chosen type,
 *     surfaces only the fields that type needs.
 *
 * Editing an existing question skips the picker and reopens the
 * editor with the question's existing type locked in (changing the
 * type of an authored question would invalidate any answers, and
 * draft quizzes have none, so it's a safe no-op to disallow it for
 * UX simplicity — host can delete + re-add).
 *
 * Validation for "Open lobby":
 *  - SINGLE: prompt + 4 non-blank labels + exactly 1 correct.
 *  - BOOLEAN: prompt + 1 correct (labels are pre-baked).
 *  - MULTIPLE: prompt + 4 non-blank labels + 1+ correct.
 *  - NUMERIC: prompt + non-null expected number + tolerance >= 0.
 */
@Composable
fun HostSetupScreen(
    quiz: TriviaQuiz,
    questions: List<TriviaQuestion>,
    choicesByQuestion: Map<String, List<TriviaChoice>>,
    isWorking: Boolean,
    onAddQuestion: (QuestionPayload) -> Unit,
    onUpdateQuestion: (questionId: String, QuestionPayload) -> Unit,
    onDeleteQuestion: (questionId: String) -> Unit,
    onOpenLobby: () -> Unit,
    /** Bounce back to the trivia list without changing the quiz. */
    onBack: () -> Unit,
    /** Permanently destroy the quiz (and its questions / choices via CASCADE). */
    onDeleteQuiz: () -> Unit,
) {
    var typePickerOpen by remember { mutableStateOf(false) }
    var editorState by remember { mutableStateOf<EditorState?>(null) }

    val canOpenLobby = !isWorking &&
        questions.isNotEmpty() &&
        questions.all { it.isReadyForLobby(choicesByQuestion[it.id].orEmpty()) }

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
                        onClick = { typePickerOpen = true },
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
                                editorState = EditorState.fromQuestion(
                                    q,
                                    choicesByQuestion[q.id].orEmpty(),
                                    quiz.defaultSecondsPerQuestion,
                                )
                            },
                            onDelete = { onDeleteQuestion(q.id) },
                        )
                    }
                    item {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { typePickerOpen = true },
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

    if (typePickerOpen) {
        TypePickerDialog(
            onCancel = { typePickerOpen = false },
            onPick = { type ->
                typePickerOpen = false
                editorState = EditorState.forNew(type, quiz.defaultSecondsPerQuestion)
            },
        )
    }

    editorState?.let { state ->
        QuestionEditorDialog(
            state = state,
            onCancel = { editorState = null },
            onSave = { payload ->
                editorState = null
                if (state.existingId == null) {
                    onAddQuestion(payload)
                } else {
                    onUpdateQuestion(state.existingId, payload)
                }
            },
        )
    }
}

/** Per-type readiness check used by the "Open lobby" gate. */
private fun TriviaQuestion.isReadyForLobby(choices: List<TriviaChoice>): Boolean {
    if (prompt.isBlank()) return false
    return when (type) {
        TriviaQuestionType.SINGLE ->
            choices.size == 4 && choices.all { it.label.isNotBlank() } && choices.count { it.isCorrect } == 1
        TriviaQuestionType.BOOLEAN ->
            choices.size == 2 && choices.count { it.isCorrect } == 1
        TriviaQuestionType.MULTIPLE ->
            choices.size == 4 && choices.all { it.label.isNotBlank() } && choices.any { it.isCorrect }
        TriviaQuestionType.NUMERIC ->
            expectedNumber != null && numericTolerance >= 0
    }
}

// ---------------------------------------------------------------- Type picker

/**
 * 2x2 grid of cards (Single / Boolean / Multiple / Numeric). Each
 * card carries a colored icon panel + name + short description so
 * the host can pick at a glance.
 */
@Composable
private fun TypePickerDialog(
    onCancel: () -> Unit,
    onPick: (TriviaQuestionType) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.trivia_type_pick_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(Res.string.trivia_type_pick_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeCard(
                        type = TriviaQuestionType.SINGLE,
                        title = Res.string.trivia_type_single,
                        description = Res.string.trivia_type_single_desc,
                        accent = TriviaPalette.backgrounds[0],
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                    TypeCard(
                        type = TriviaQuestionType.BOOLEAN,
                        title = Res.string.trivia_type_boolean,
                        description = Res.string.trivia_type_boolean_desc,
                        accent = TriviaPalette.backgrounds[2],
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TypeCard(
                        type = TriviaQuestionType.MULTIPLE,
                        title = Res.string.trivia_type_multiple,
                        description = Res.string.trivia_type_multiple_desc,
                        accent = TriviaPalette.backgrounds[1],
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                    TypeCard(
                        type = TriviaQuestionType.NUMERIC,
                        title = Res.string.trivia_type_numeric,
                        description = Res.string.trivia_type_numeric_desc,
                        accent = TriviaPalette.backgrounds[3],
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
private fun TypeCard(
    type: TriviaQuestionType,
    title: StringResource,
    description: StringResource,
    accent: Color,
    onPick: (TriviaQuestionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable { onPick(type) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = TYPE_GLYPHS[type] ?: "?",
                    color = TriviaPalette.foregrounds[type.paletteIndex()],
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(description),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun TriviaQuestionType.paletteIndex(): Int = when (this) {
    TriviaQuestionType.SINGLE -> 0
    TriviaQuestionType.MULTIPLE -> 1
    TriviaQuestionType.BOOLEAN -> 2
    TriviaQuestionType.NUMERIC -> 3
}

private val TYPE_GLYPHS: Map<TriviaQuestionType, String> = mapOf(
    TriviaQuestionType.SINGLE to "1",
    TriviaQuestionType.BOOLEAN to "T/F",
    TriviaQuestionType.MULTIPLE to "N",
    TriviaQuestionType.NUMERIC to "12",
)

// ---------------------------------------------------------------- Summary card

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
                )
                Spacer(Modifier.size(8.dp))
                TypePill(type = question.type)
                Spacer(Modifier.weight(1f))
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
            // Body changes per type so the host gets a read-only preview
            // matching what players will see.
            when (question.type) {
                TriviaQuestionType.SINGLE,
                TriviaQuestionType.MULTIPLE -> ChoiceListPreview(ordered, expectedSize = 4)
                TriviaQuestionType.BOOLEAN -> ChoiceListPreview(ordered, expectedSize = 2)
                TriviaQuestionType.NUMERIC -> NumericPreview(question)
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
private fun ChoiceListPreview(ordered: List<TriviaChoice>, expectedSize: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ordered.take(expectedSize).forEachIndexed { idx, choice ->
            ChoiceChip(
                index = idx,
                label = choice.label.ifBlank { "—" },
                isCorrect = choice.isCorrect,
            )
        }
        repeat((expectedSize - ordered.size).coerceAtLeast(0)) { idx ->
            ChoiceChip(
                index = ordered.size + idx,
                label = "—",
                isCorrect = false,
            )
        }
    }
}

@Composable
private fun NumericPreview(question: TriviaQuestion) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.trivia_numeric_expected),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatNumeric(question.expectedNumber),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (question.numericTolerance > 0) {
            Spacer(Modifier.size(8.dp))
            Text(
                "+/- ${formatNumeric(question.numericTolerance)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypePill(type: TriviaQuestionType) {
    val accent = TriviaPalette.backgrounds[type.paletteIndex()]
    val onAccent = TriviaPalette.foregrounds[type.paletteIndex()]
    val label = stringResource(
        when (type) {
            TriviaQuestionType.SINGLE -> Res.string.trivia_type_single
            TriviaQuestionType.BOOLEAN -> Res.string.trivia_type_boolean
            TriviaQuestionType.MULTIPLE -> Res.string.trivia_type_multiple
            TriviaQuestionType.NUMERIC -> Res.string.trivia_type_numeric
        },
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.85f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = onAccent,
            fontWeight = FontWeight.Bold,
        )
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
        TriviaShapeIcon(index = index, color = fg, size = 14.dp, modifier = Modifier.padding(end = 8.dp))
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
                IconCheck(tint = bg, size = 14.dp)
            }
        }
    }
}

// ---------------------------------------------------------------- Editor state

private data class EditorState(
    val existingId: String?,
    val type: TriviaQuestionType,
    val initialPrompt: String,
    val initialSeconds: Int,
    val initialLabels: List<String>,
    val initialCorrectIndices: Set<Int>,
    val initialExpectedNumber: String,   // string so the input can be empty
    val initialTolerance: String,
) {
    companion object {
        fun forNew(type: TriviaQuestionType, defaultSeconds: Int): EditorState {
            val labels = when (type) {
                TriviaQuestionType.SINGLE,
                TriviaQuestionType.MULTIPLE -> List(4) { "" }
                TriviaQuestionType.BOOLEAN -> emptyList() // pre-baked at save
                TriviaQuestionType.NUMERIC -> emptyList()
            }
            return EditorState(
                existingId = null,
                type = type,
                initialPrompt = "",
                initialSeconds = defaultSeconds,
                initialLabels = labels,
                initialCorrectIndices = emptySet(),
                initialExpectedNumber = "",
                initialTolerance = "0",
            )
        }

        fun fromQuestion(
            q: TriviaQuestion,
            choices: List<TriviaChoice>,
            defaultSeconds: Int,
        ): EditorState {
            val ordered = choices.sortedBy { it.position }
            val labels = when (q.type) {
                TriviaQuestionType.SINGLE,
                TriviaQuestionType.MULTIPLE ->
                    (ordered.map { it.label } + List(4) { "" }).take(4)
                TriviaQuestionType.BOOLEAN -> emptyList()
                TriviaQuestionType.NUMERIC -> emptyList()
            }
            val correct = when (q.type) {
                TriviaQuestionType.SINGLE,
                TriviaQuestionType.MULTIPLE ->
                    ordered.filter { it.isCorrect }.map { it.position }.toSet()
                TriviaQuestionType.BOOLEAN ->
                    // Boolean stores true at position 0, false at position 1 by
                    // convention. The picker holds 0 = "true correct", 1 = "false correct".
                    ordered.firstOrNull { it.isCorrect }?.position?.let { setOf(it) } ?: emptySet()
                TriviaQuestionType.NUMERIC -> emptySet()
            }
            return EditorState(
                existingId = q.id,
                type = q.type,
                initialPrompt = q.prompt,
                initialSeconds = if (q.secondsToAnswer > 0) q.secondsToAnswer else defaultSeconds,
                initialLabels = labels,
                initialCorrectIndices = correct,
                initialExpectedNumber = q.expectedNumber?.let { formatNumeric(it) } ?: "",
                initialTolerance = formatNumeric(q.numericTolerance),
            )
        }
    }
}

// ---------------------------------------------------------------- Editor dialog

@Composable
private fun QuestionEditorDialog(
    state: EditorState,
    onCancel: () -> Unit,
    onSave: (QuestionPayload) -> Unit,
) {
    var prompt by remember(state) { mutableStateOf(state.initialPrompt) }
    var seconds by remember(state) { mutableIntStateOf(state.initialSeconds) }
    val labels = remember(state) {
        mutableStateListOf<String>().also { it.addAll(state.initialLabels) }
    }
    val correctIndices = remember(state) {
        mutableStateListOf<Int>().also { it.addAll(state.initialCorrectIndices) }
    }
    var expectedRaw by remember(state) { mutableStateOf(state.initialExpectedNumber) }
    var toleranceRaw by remember(state) { mutableStateOf(state.initialTolerance) }

    val canSave = prompt.isNotBlank() && when (state.type) {
        TriviaQuestionType.SINGLE ->
            labels.all { it.isNotBlank() } && correctIndices.size == 1
        TriviaQuestionType.MULTIPLE ->
            labels.all { it.isNotBlank() } && correctIndices.isNotEmpty()
        TriviaQuestionType.BOOLEAN ->
            correctIndices.size == 1
        TriviaQuestionType.NUMERIC ->
            expectedRaw.toDoubleOrNull() != null &&
                (toleranceRaw.toDoubleOrNull()?.let { it >= 0 } ?: false)
    }

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
            // Wrap in a vertically-scrollable column so the numeric +
            // multiple variants don't overflow on small screens.
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.trivia_question_type_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                    TypePill(type = state.type)
                }
                Spacer(Modifier.height(10.dp))
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
                when (state.type) {
                    TriviaQuestionType.SINGLE ->
                        SingleChoiceFields(labels, correctIndices)
                    TriviaQuestionType.BOOLEAN ->
                        BooleanFields(correctIndices)
                    TriviaQuestionType.MULTIPLE ->
                        MultipleChoiceFields(labels, correctIndices)
                    TriviaQuestionType.NUMERIC ->
                        NumericFields(
                            expectedRaw = expectedRaw,
                            toleranceRaw = toleranceRaw,
                            onExpectedChange = { expectedRaw = it },
                            onToleranceChange = { toleranceRaw = it },
                        )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val payload = buildPayload(
                        type = state.type,
                        prompt = prompt,
                        seconds = seconds,
                        labels = labels.toList(),
                        correctIndices = correctIndices.toSet(),
                        expectedRaw = expectedRaw,
                        toleranceRaw = toleranceRaw,
                    )
                    onSave(payload)
                },
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

@Composable
private fun SingleChoiceFields(
    labels: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    correctIndices: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
) {
    labels.forEachIndexed { idx, _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = idx in correctIndices,
                onClick = {
                    correctIndices.clear()
                    correctIndices.add(idx)
                },
            )
            ChoiceColorDot(index = idx)
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

@Composable
private fun MultipleChoiceFields(
    labels: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    correctIndices: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
) {
    labels.forEachIndexed { idx, _ ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = idx in correctIndices,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (idx !in correctIndices) correctIndices.add(idx)
                    } else {
                        correctIndices.remove(idx)
                    }
                },
            )
            ChoiceColorDot(index = idx)
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
        stringResource(Res.string.trivia_choice_correct_multiple),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BooleanFields(
    correctIndices: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BooleanOption(
            label = stringResource(Res.string.trivia_boolean_label_true),
            isSelected = 0 in correctIndices,
            accent = TriviaPalette.backgrounds[1], // green
            onTap = {
                correctIndices.clear()
                correctIndices.add(0)
            },
            modifier = Modifier.weight(1f),
        )
        BooleanOption(
            label = stringResource(Res.string.trivia_boolean_label_false),
            isSelected = 1 in correctIndices,
            accent = TriviaPalette.backgrounds[0], // red
            onTap = {
                correctIndices.clear()
                correctIndices.add(1)
            },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(Res.string.trivia_choice_correct),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BooleanOption(
    label: String,
    isSelected: Boolean,
    accent: Color,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accent else accent.copy(alpha = 0.18f))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = accent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onTap)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else accent,
        )
    }
}

@Composable
private fun NumericFields(
    expectedRaw: String,
    toleranceRaw: String,
    onExpectedChange: (String) -> Unit,
    onToleranceChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = expectedRaw,
        onValueChange = { onExpectedChange(it.filterNumericInput()) },
        label = { Text(stringResource(Res.string.trivia_numeric_expected)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = toleranceRaw,
        onValueChange = { onToleranceChange(it.filterNumericInput(allowNegative = false)) },
        label = { Text(stringResource(Res.string.trivia_numeric_tolerance)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(Res.string.trivia_numeric_tolerance_helper),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ChoiceColorDot(index: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(TriviaPalette.backgrounds[index]),
        contentAlignment = Alignment.Center,
    ) {
        TriviaShapeIcon(index = index, color = TriviaPalette.foregrounds[index], size = 12.dp)
    }
}

// ---------------------------------------------------------------- Helpers

/**
 * Strips characters that don't belong in a decimal input. Keeps a
 * single sign char (only at position 0; suppressed entirely when
 * [allowNegative] is false) and a single decimal separator (accepts
 * both `.` and `,` so users with locales that use comma can type
 * naturally — both are normalized to `.` before parsing in
 * [buildPayload]).
 */
private fun String.filterNumericInput(allowNegative: Boolean = true): String {
    val sb = StringBuilder()
    var seenDot = false
    for ((idx, c) in withIndex()) {
        when {
            c.isDigit() -> sb.append(c)
            (c == '.' || c == ',') && !seenDot -> {
                sb.append('.')
                seenDot = true
            }
            c == '-' && allowNegative && idx == 0 && sb.isEmpty() -> sb.append('-')
            else -> Unit
        }
    }
    return sb.toString()
}

/** Pretty-prints a number for the read-only summary card. */
internal fun formatNumeric(value: Double?): String {
    if (value == null) return "—"
    val asLong = value.toLong()
    return if (asLong.toDouble() == value) asLong.toString() else value.toString()
}

private fun buildPayload(
    type: TriviaQuestionType,
    prompt: String,
    seconds: Int,
    labels: List<String>,
    correctIndices: Set<Int>,
    expectedRaw: String,
    toleranceRaw: String,
): QuestionPayload {
    val resolvedLabels = when (type) {
        TriviaQuestionType.SINGLE,
        TriviaQuestionType.MULTIPLE -> labels.take(4)
        TriviaQuestionType.BOOLEAN -> emptyList()
        TriviaQuestionType.NUMERIC -> emptyList()
    }
    return QuestionPayload(
        type = type,
        prompt = prompt,
        secondsToAnswer = seconds,
        labels = resolvedLabels,
        correctIndices = correctIndices,
        expectedNumber = if (type == TriviaQuestionType.NUMERIC) {
            expectedRaw.toDoubleOrNull()
        } else {
            null
        },
        numericTolerance = if (type == TriviaQuestionType.NUMERIC) {
            toleranceRaw.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        } else {
            0.0
        },
    )
}
