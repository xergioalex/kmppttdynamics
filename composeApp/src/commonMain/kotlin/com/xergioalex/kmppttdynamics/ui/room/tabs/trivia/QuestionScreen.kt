package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.ui.components.IconCheck
import com.xergioalex.kmppttdynamics.ui.components.IconCircleOutline
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.trivia.TriviaAnswer
import com.xergioalex.kmppttdynamics.trivia.TriviaChoice
import com.xergioalex.kmppttdynamics.trivia.TriviaQuestion
import com.xergioalex.kmppttdynamics.trivia.TriviaQuestionType
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.trivia_answered_count
import kmppttdynamics.composeapp.generated.resources.trivia_boolean_label_false
import kmppttdynamics.composeapp.generated.resources.trivia_boolean_label_true
import kmppttdynamics.composeapp.generated.resources.trivia_correct
import kmppttdynamics.composeapp.generated.resources.trivia_locked_in
import kmppttdynamics.composeapp.generated.resources.trivia_multiple_hint
import kmppttdynamics.composeapp.generated.resources.trivia_multiple_submit
import kmppttdynamics.composeapp.generated.resources.trivia_multiple_submit_empty
import kmppttdynamics.composeapp.generated.resources.trivia_numeric_expected
import kmppttdynamics.composeapp.generated.resources.trivia_numeric_input_hint
import kmppttdynamics.composeapp.generated.resources.trivia_numeric_submit
import kmppttdynamics.composeapp.generated.resources.trivia_spectating
import kmppttdynamics.composeapp.generated.resources.trivia_question_header
import kmppttdynamics.composeapp.generated.resources.trivia_seconds_remaining
import kmppttdynamics.composeapp.generated.resources.trivia_skip
import kmppttdynamics.composeapp.generated.resources.trivia_time_up
import kmppttdynamics.composeapp.generated.resources.trivia_wrong
import kotlin.math.max
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Live gameplay screen. Renders the active question, the four colored
 * choice buttons, the animated countdown ring, and the brief reveal
 * overlay between questions.
 *
 * Timing model:
 *   - Server stamps `current_question_started_at` when the host
 *     advances. This is the single source of truth.
 *   - Each client computes `remaining = (started + window) - now`
 *     locally — the visual countdown is just rendering, the lifecycle
 *     decisions all key off the server timestamp.
 *   - When `remaining` hits zero we flip into "reveal" for
 *     `TriviaTiming.ANSWER_REVEAL_MS`, then the host's device fires
 *     `advanceQuestion` (the WHERE-guarded UPDATE makes concurrent
 *     fires idempotent so non-host devices firing it is also safe;
 *     we keep it host-only here for predictability).
 *
 * Network blip: the screen subscribes to `observeAnswers` so the
 * "X / N answered" counter updates live for the host. The user's own
 * answer is identified by `client_id`, not `participant_id`, so even
 * if the participant row is recreated mid-quiz the answer state is
 * preserved.
 */
@Composable
fun QuestionScreen(
    quiz: TriviaQuiz,
    questions: List<TriviaQuestion>,
    choicesByQuestion: Map<String, List<TriviaChoice>>,
    container: AppContainer,
    me: MeetupParticipant,
    myClientId: String,
    isHost: Boolean,
    isWorking: Boolean,
    /**
     * Count of online participants in the room. Used as the
     * denominator on the host's "X / N answered" counter so the
     * host doesn't see "3 / 12" when 5 of the 12 are offline.
     */
    onlineCount: Int,
    /**
     * True when this device is enrolled in the quiz (its row exists
     * in `trivia_entries`). Determines whether the four colored choice
     * buttons are tappable. Non-enrolled devices stay in spectator
     * mode: countdown + prompt + reveal animations are visible but
     * the buttons are disabled with a "you're spectating" hint.
     */
    canAnswer: Boolean,
    onAdvance: (expectedIndex: Int) -> Unit,
) {
    val index = quiz.currentQuestionIndex ?: 0
    val total = questions.size
    val question = questions.getOrNull(index)

    if (question == null || total == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val choices = (choicesByQuestion[question.id].orEmpty())
        .sortedBy { it.position }

    val answerScope = rememberCoroutineScope()
    var answers by remember(quiz.id) { mutableStateOf<List<TriviaAnswer>>(emptyList()) }
    LaunchedEffect(quiz.id) {
        container.trivia.observeAnswers(quiz.id)
            .catch { /* non-fatal; the realtime feed will retry */ }
            .collect { answers = it }
    }

    // ---- Local clock tick (visual only) -------------------------------
    val startedAt = quiz.currentQuestionStartedAt
    val totalMs = (question.secondsToAnswer * 1000L).coerceAtLeast(1L)
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(quiz.id, index) {
        while (true) {
            nowMs = Clock.System.now().toEpochMilliseconds()
            delay(50L)
        }
    }
    val elapsedMs by remember(startedAt, nowMs) {
        derivedStateOf {
            val started = startedAt?.toEpochMilliseconds() ?: nowMs
            (nowMs - started).coerceAtLeast(0L)
        }
    }
    val remainingMs = max(0L, totalMs - elapsedMs)
    val remainingSec = ((remainingMs + 999L) / 1000L).toInt()
    val timeUp = remainingMs <= 0L

    // ---- My answer + per-question summary -----------------------------
    val myAnswer = remember(answers, question.id, myClientId) {
        answers.firstOrNull { it.questionId == question.id && it.clientId == myClientId }
    }
    val answersForCurrentQ = remember(answers, question.id) {
        answers.count { it.questionId == question.id }
    }
    val correctChoiceId = remember(choices) {
        choices.firstOrNull { it.isCorrect }?.id
    }
    val correctChoiceIds = remember(choices) {
        choices.filter { it.isCorrect }.map { it.id }.toSet()
    }

    // ---- Reveal lifecycle ---------------------------------------------
    var revealing by remember(quiz.id, index) { mutableStateOf(false) }
    LaunchedEffect(quiz.id, index, timeUp) {
        if (!timeUp) return@LaunchedEffect
        revealing = true
        delay(TriviaTiming.ANSWER_REVEAL_MS.toLong())
        if (isHost) {
            // Only the host fires the advance to keep network chatter low.
            // The repository's WHERE-guarded UPDATE makes this idempotent
            // anyway, so we could broadcast it from every device, but
            // chosen path keeps the realtime feed quiet.
            onAdvance(index)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // -- Header: Q n / N + countdown ring ---------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.trivia_question_header, index + 1, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            CountdownRing(
                remainingMs = remainingMs,
                totalMs = totalMs,
            )
        }
        Spacer(Modifier.height(16.dp))

        // Slide the prompt + choices in from the right whenever the
        // current question advances, and slide the previous frame out
        // to the left. Compose `AnimatedContent` keys off `index` so
        // the transition only fires on real Q→Q+1 transitions, not on
        // every recomposition.
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 320, easing = EaseOutCubic),
                ) + fadeIn(animationSpec = tween(220))) togetherWith
                    (slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(durationMillis = 240),
                    ) + fadeOut(animationSpec = tween(180)))
            },
            label = "trivia-question-transition",
        ) { animatedIndex ->
            // Look up the question for the animated index so the
            // outgoing frame keeps rendering the previous question
            // even after `questions.getOrNull(index)` has moved on.
            val animatedQuestion = questions.getOrNull(animatedIndex) ?: question
            val animatedChoices = (choicesByQuestion[animatedQuestion.id].orEmpty())
                .sortedBy { it.position }
            val live = animatedIndex == index

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        animatedQuestion.prompt,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                // Per-type gameplay body. We pass `live` so the
                // outgoing frame stays inert during the slide-out.
                when (animatedQuestion.type) {
                    TriviaQuestionType.SINGLE ->
                        SingleChoiceBody(
                            question = animatedQuestion,
                            choices = animatedChoices.take(4),
                            live = live,
                            canAnswer = canAnswer,
                            myAnswer = myAnswer,
                            revealing = revealing,
                            timeUp = timeUp,
                            correctChoiceId = correctChoiceId,
                            onPick = { choice ->
                                fireSingleAnswer(answerScope, container, quiz, question, choice, me, myClientId)
                            },
                        )
                    TriviaQuestionType.BOOLEAN ->
                        BooleanBody(
                            choices = animatedChoices.take(2),
                            live = live,
                            canAnswer = canAnswer,
                            myAnswer = myAnswer,
                            revealing = revealing,
                            timeUp = timeUp,
                            correctChoiceId = correctChoiceId,
                            onPick = { choice ->
                                fireSingleAnswer(answerScope, container, quiz, question, choice, me, myClientId)
                            },
                        )
                    TriviaQuestionType.MULTIPLE ->
                        MultipleChoiceBody(
                            question = animatedQuestion,
                            choices = animatedChoices.take(4),
                            live = live,
                            canAnswer = canAnswer,
                            myAnswer = myAnswer,
                            revealing = revealing,
                            timeUp = timeUp,
                            correctChoiceIds = correctChoiceIds,
                            onSubmit = { selectedIds ->
                                answerScope.launch {
                                    runCatching {
                                        container.trivia.answerMultiple(
                                            quizId = quiz.id,
                                            questionId = question.id,
                                            choiceIds = selectedIds,
                                            participantId = me.id,
                                            clientId = myClientId,
                                        )
                                    }.onFailure {
                                        println("TriviaQuestion.answerMultiple failed: ${it::class.simpleName}: ${it.message}")
                                    }
                                }
                            },
                        )
                    TriviaQuestionType.NUMERIC ->
                        NumericBody(
                            question = animatedQuestion,
                            live = live,
                            canAnswer = canAnswer,
                            myAnswer = myAnswer,
                            revealing = revealing,
                            timeUp = timeUp,
                            onSubmit = { value ->
                                answerScope.launch {
                                    runCatching {
                                        container.trivia.answerNumeric(
                                            quizId = quiz.id,
                                            questionId = question.id,
                                            value = value,
                                            participantId = me.id,
                                            clientId = myClientId,
                                        )
                                    }.onFailure {
                                        println("TriviaQuestion.answerNumeric failed: ${it::class.simpleName}: ${it.message}")
                                    }
                                }
                            },
                        )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // -- Footer: locked-in / spectator hint + host counters ---------
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!canAnswer) {
                Text(
                    stringResource(Res.string.trivia_spectating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (myAnswer != null && !revealing) {
                Text(
                    stringResource(Res.string.trivia_locked_in),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (isHost) {
                Text(
                    stringResource(
                        Res.string.trivia_answered_count,
                        answersForCurrentQ,
                        onlineCount.coerceAtLeast(answersForCurrentQ),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!revealing) {
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = { onAdvance(index) },
                        enabled = !isWorking,
                    ) { Text(stringResource(Res.string.trivia_skip)) }
                }
            }
        }

        // -- Reveal overlay -----------------------------------------
        AnimatedVisibility(
            visible = revealing,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut(),
        ) {
            RevealBanner(
                points = myAnswer?.pointsAwarded ?: 0,
                wasCorrect = myAnswer?.isCorrect == true,
                hadAnswer = myAnswer != null,
            )
        }
    }
}

/**
 * Animated countdown arc. Color shifts from secondary → tertiary → error
 * as time runs out, and pulses in the last [TriviaTiming.LAST_SECONDS_PULSE]
 * seconds for urgency. Sized at 88 dp by default so the seconds digit
 * is the most prominent thing on the screen during gameplay.
 */
@Composable
private fun CountdownRing(remainingMs: Long, totalMs: Long, sizeDp: Int = 88) {
    val progress = (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "trivia-countdown-progress",
    )
    val secondsRemaining = ((remainingMs + 999L) / 1000L).toInt()
    val urgent = secondsRemaining <= TriviaTiming.LAST_SECONDS_PULSE && secondsRemaining > 0

    // Compose composables (rememberInfiniteTransition / animateFloat)
    // must be called unconditionally; we always run the pulse animator
    // and only consume its value when the urgent threshold is hit.
    val pulseTransition = rememberInfiniteTransition(label = "trivia-countdown-pulse")
    val pulseValue by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trivia-countdown-pulse-value",
    )
    val pulse = if (urgent) pulseValue else 1f

    val ok = MaterialTheme.colorScheme.secondary
    val warn = MaterialTheme.colorScheme.tertiary
    val danger = MaterialTheme.colorScheme.error
    val ringColor = when {
        progress > 0.5f -> ok
        progress > 0.25f -> lerp(warn, ok, ((progress - 0.25f) / 0.25f).coerceIn(0f, 1f))
        else -> lerp(danger, warn, (progress / 0.25f).coerceIn(0f, 1f))
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.size(sizeDp.dp).scale(pulse),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8f
            val diameter = size.minDimension - stroke
            val topLeft = Offset(
                x = (size.width - diameter) / 2,
                y = (size.height - diameter) / 2,
            )
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }
        Text(
            text = stringResource(Res.string.trivia_seconds_remaining, secondsRemaining),
            color = onSurface,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/**
 * One of the four large Kahoot-style choice buttons. Stays interactive
 * until the user has answered or the timer expires; during the reveal
 * overlay a check mark on a white disk marks the correct answer, and
 * the user's losing pick (if any) is dimmed.
 *
 * Press feedback: scales to 96 % while held so the tap feels tactile
 * even before the realtime answer round-trips back. We drive this from
 * the [interactionSource]'s pressed flag so the visual cue is perfectly
 * synchronized with the click event.
 */
@Composable
private fun ChoiceButton(
    index: Int,
    label: String,
    locked: Boolean,
    isMine: Boolean,
    revealCorrect: Boolean,
    revealMineWrong: Boolean,
    onClick: () -> Unit,
) {
    val baseBg = TriviaPalette.backgrounds[index]
    val fg = TriviaPalette.foregrounds[index]
    val target = when {
        revealCorrect -> baseBg
        revealMineWrong -> baseBg.copy(alpha = 0.35f)
        locked && !isMine -> baseBg.copy(alpha = 0.45f)
        else -> baseBg
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !locked) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120, easing = EaseOutCubic),
        label = "trivia-choice-press",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clip(RoundedCornerShape(14.dp))
            .background(target)
            .clickable(
                enabled = !locked,
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                TriviaShapeIcon(index = index, color = fg, size = 18.dp)
            }
            Spacer(Modifier.size(12.dp))
            Text(
                label,
                color = fg,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (revealCorrect) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    IconCheck(tint = baseBg, size = 16.dp)
                }
            } else if (isMine) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconCircleOutline(tint = baseBg, size = 16.dp)
                }
            }
        }
    }
}

/**
 * Toast-like banner that flashes between questions. Tells the user how
 * many points they earned (or that they didn't answer in time). The
 * +N value rolls up from 0 over ~700 ms with an ease-out curve so the
 * positive feedback feels earned, not abrupt.
 */
@Composable
private fun RevealBanner(points: Int, wasCorrect: Boolean, hadAnswer: Boolean) {
    // Animate the score counter from 0 → points whenever the banner
    // remounts (each new question gets a fresh keyed Animatable).
    val animated = remember(points, wasCorrect, hadAnswer) { Animatable(0f) }
    LaunchedEffect(points, wasCorrect, hadAnswer) {
        if (wasCorrect && points > 0) {
            animated.animateTo(
                targetValue = points.toFloat(),
                animationSpec = tween(durationMillis = 700, easing = EaseOutCubic),
            )
        } else {
            animated.snapTo(0f)
        }
    }
    val (bg, fg) = when {
        !hadAnswer -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
        wasCorrect -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
    val label = when {
        !hadAnswer -> stringResource(Res.string.trivia_time_up)
        wasCorrect -> stringResource(Res.string.trivia_correct, animated.value.toInt())
        else -> stringResource(Res.string.trivia_wrong)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

// =============================================================
//  Per-type gameplay bodies
// =============================================================

/**
 * Helper that launches a fire-and-forget single-choice answer. Used
 * by both SINGLE and BOOLEAN bodies because the wire payload is
 * identical (one `choice_id`).
 */
private fun fireSingleAnswer(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    quiz: TriviaQuiz,
    question: TriviaQuestion,
    choice: TriviaChoice,
    me: MeetupParticipant,
    myClientId: String,
) {
    scope.launch {
        runCatching {
            container.trivia.answer(
                quizId = quiz.id,
                questionId = question.id,
                choiceId = choice.id,
                participantId = me.id,
                clientId = myClientId,
            )
        }.onFailure {
            println("TriviaQuestion.answer failed: ${it::class.simpleName}: ${it.message}")
        }
    }
}

@Composable
private fun SingleChoiceBody(
    question: TriviaQuestion,
    choices: List<TriviaChoice>,
    live: Boolean,
    canAnswer: Boolean,
    myAnswer: TriviaAnswer?,
    revealing: Boolean,
    timeUp: Boolean,
    correctChoiceId: String?,
    onPick: (TriviaChoice) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        choices.forEachIndexed { idx, choice ->
            val isMyChoice = live && myAnswer?.choiceId == choice.id
            val showCorrect = live && revealing && choice.id == correctChoiceId
            val showIncorrectMine = live && revealing && isMyChoice && choice.id != correctChoiceId
            ChoiceButton(
                index = idx,
                label = choice.label,
                locked = !live || !canAnswer || myAnswer != null || timeUp,
                isMine = isMyChoice,
                revealCorrect = showCorrect,
                revealMineWrong = showIncorrectMine,
                onClick = {
                    if (!live || !canAnswer || myAnswer != null || timeUp) return@ChoiceButton
                    onPick(choice)
                },
            )
        }
    }
}

/**
 * True / False gameplay. Renders two large stacked tiles with the
 * existing palette (green for True, red for False), localized
 * labels (the DB labels are always English placeholders), and a
 * giant glyph so the choice reads from across the room.
 */
@Composable
private fun BooleanBody(
    choices: List<TriviaChoice>,
    live: Boolean,
    canAnswer: Boolean,
    myAnswer: TriviaAnswer?,
    revealing: Boolean,
    timeUp: Boolean,
    correctChoiceId: String?,
    onPick: (TriviaChoice) -> Unit,
) {
    val trueLabel = stringResource(Res.string.trivia_boolean_label_true)
    val falseLabel = stringResource(Res.string.trivia_boolean_label_false)
    val trueChoice = choices.getOrNull(0)
    val falseChoice = choices.getOrNull(1)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BooleanTile(
            choice = trueChoice,
            label = trueLabel,
            glyph = "T",
            accent = TriviaPalette.backgrounds[1], // green
            isMine = trueChoice != null && live && myAnswer?.choiceId == trueChoice.id,
            revealCorrect = trueChoice != null && live && revealing && trueChoice.id == correctChoiceId,
            revealMineWrong = trueChoice != null && live && revealing &&
                myAnswer?.choiceId == trueChoice.id && trueChoice.id != correctChoiceId,
            locked = !live || !canAnswer || myAnswer != null || timeUp,
            onClick = { trueChoice?.let(onPick) },
        )
        BooleanTile(
            choice = falseChoice,
            label = falseLabel,
            glyph = "F",
            accent = TriviaPalette.backgrounds[0], // red
            isMine = falseChoice != null && live && myAnswer?.choiceId == falseChoice.id,
            revealCorrect = falseChoice != null && live && revealing && falseChoice.id == correctChoiceId,
            revealMineWrong = falseChoice != null && live && revealing &&
                myAnswer?.choiceId == falseChoice.id && falseChoice.id != correctChoiceId,
            locked = !live || !canAnswer || myAnswer != null || timeUp,
            onClick = { falseChoice?.let(onPick) },
        )
    }
}

@Composable
private fun BooleanTile(
    choice: TriviaChoice?,
    label: String,
    glyph: String,
    accent: Color,
    isMine: Boolean,
    revealCorrect: Boolean,
    revealMineWrong: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val target = when {
        revealCorrect -> accent
        revealMineWrong -> accent.copy(alpha = 0.35f)
        locked && !isMine -> accent.copy(alpha = 0.45f)
        else -> accent
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !locked) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120, easing = EaseOutCubic),
        label = "trivia-bool-press",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clip(RoundedCornerShape(14.dp))
            .background(target)
            .clickable(
                enabled = !locked && choice != null,
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                glyph,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Spacer(Modifier.size(16.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        if (revealCorrect) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                IconCheck(tint = accent, size = 18.dp)
            }
        } else if (isMine) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                IconCircleOutline(tint = accent, size = 18.dp)
            }
        }
    }
}

/**
 * Multiple-choice gameplay. Each tile toggles inclusion until the
 * user taps Submit; once submitted, all tiles lock and the reveal
 * highlights every correct choice + flags the user's misses /
 * extra picks.
 */
@Composable
private fun MultipleChoiceBody(
    question: TriviaQuestion,
    choices: List<TriviaChoice>,
    live: Boolean,
    canAnswer: Boolean,
    myAnswer: TriviaAnswer?,
    revealing: Boolean,
    timeUp: Boolean,
    correctChoiceIds: Set<String>,
    onSubmit: (List<String>) -> Unit,
) {
    // Local pre-submit selection. Resets every time the question
    // changes (key includes question.id).
    val selected = remember(question.id) { mutableStateListOf<String>() }
    val submittedIds = remember(myAnswer) {
        myAnswer?.choiceIds?.toSet() ?: emptySet()
    }
    val locked = !live || !canAnswer || myAnswer != null || timeUp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (live && canAnswer && myAnswer == null && !timeUp) {
            Text(
                stringResource(Res.string.trivia_multiple_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        choices.forEachIndexed { idx, choice ->
            val isLocallySelected = choice.id in selected
            val isInSubmitted = choice.id in submittedIds
            val isMine = isInSubmitted || (myAnswer == null && isLocallySelected)
            val showCorrect = live && revealing && choice.id in correctChoiceIds
            val showIncorrectMine = live && revealing && isInSubmitted && choice.id !in correctChoiceIds
            ChoiceButton(
                index = idx,
                label = choice.label,
                locked = locked,
                isMine = isMine,
                revealCorrect = showCorrect,
                revealMineWrong = showIncorrectMine,
                onClick = {
                    if (locked) return@ChoiceButton
                    if (choice.id in selected) selected.remove(choice.id) else selected.add(choice.id)
                },
            )
        }
        // Submit row: visible only while the user can still pick.
        if (live && canAnswer && myAnswer == null && !timeUp) {
            val count = selected.size
            Button(
                onClick = { onSubmit(selected.toList()) },
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (count == 0) {
                        stringResource(Res.string.trivia_multiple_submit_empty)
                    } else {
                        stringResource(Res.string.trivia_multiple_submit, count)
                    },
                )
            }
        }
    }
}

/**
 * Numeric gameplay. A single text field accepting decimals + a
 * Submit button. On reveal we swap the editor for a static
 * "Correct: X" pill so spectators learn the answer too.
 */
@Composable
private fun NumericBody(
    question: TriviaQuestion,
    live: Boolean,
    canAnswer: Boolean,
    myAnswer: TriviaAnswer?,
    revealing: Boolean,
    timeUp: Boolean,
    onSubmit: (Double) -> Unit,
) {
    var raw by remember(question.id) { mutableStateOf("") }
    val locked = !live || !canAnswer || myAnswer != null || timeUp
    val expected = question.expectedNumber

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (revealing && expected != null) {
            // Reveal: surface the canonical correct value to everyone
            // (including spectators).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${stringResource(Res.string.trivia_numeric_expected)}: ${formatNumeric(expected)}",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (myAnswer != null) {
            // Show the user the value they submitted (echoed from
            // the realtime feed).
            val submittedText = myAnswer.numericValue?.let { formatNumeric(it) } ?: "—"
            val correct = myAnswer.isCorrect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (correct) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = submittedText,
                    color = if (correct) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (!locked) {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it.filterDecimalInput() },
                label = { Text(stringResource(Res.string.trivia_numeric_input_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val value = raw.toDoubleOrNull() ?: return@Button
                    onSubmit(value)
                },
                enabled = raw.toDoubleOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.trivia_numeric_submit))
            }
        } else if (!revealing) {
            // Time's up but no answer: leave a placeholder so the
            // body height stays roughly stable across phases.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

/**
 * Same input filter the host editor uses, lifted to file scope so
 * gameplay typing rejects letters / multiple decimal separators
 * without us re-implementing it. Accepts both `.` and `,` (locales).
 */
private fun String.filterDecimalInput(): String {
    val sb = StringBuilder()
    var seenDot = false
    for ((idx, c) in withIndex()) {
        when {
            c.isDigit() -> sb.append(c)
            (c == '.' || c == ',') && !seenDot -> {
                sb.append('.')
                seenDot = true
            }
            c == '-' && idx == 0 && sb.isEmpty() -> sb.append('-')
            else -> Unit
        }
    }
    return sb.toString()
}
