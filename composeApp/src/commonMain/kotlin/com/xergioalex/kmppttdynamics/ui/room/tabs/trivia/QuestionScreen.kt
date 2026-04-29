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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.domain.MeetupParticipant
import com.xergioalex.kmppttdynamics.trivia.TriviaAnswer
import com.xergioalex.kmppttdynamics.trivia.TriviaChoice
import com.xergioalex.kmppttdynamics.trivia.TriviaQuestion
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.trivia_answered_count
import kmppttdynamics.composeapp.generated.resources.trivia_correct
import kmppttdynamics.composeapp.generated.resources.trivia_locked_in
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
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val choices = (choicesByQuestion[question.id].orEmpty())
        .sortedBy { it.position }
        .take(4)

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
                .take(4)

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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    animatedChoices.forEachIndexed { idx, choice ->
                        // For the *current* question we drive the
                        // locked / reveal states off live data; for
                        // an outgoing previous-question frame those
                        // signals don't apply (the user already saw
                        // the reveal before the slide started).
                        val live = animatedIndex == index
                        val isMyChoice = live && myAnswer?.choiceId == choice.id
                        val showCorrect = live && revealing && choice.id == correctChoiceId
                        val showIncorrectMine = live && revealing && isMyChoice &&
                            choice.id != correctChoiceId
                        ChoiceButton(
                            index = idx,
                            label = choice.label,
                            // Spectators (not enrolled) and outgoing
                            // previous-question frames can never tap;
                            // also covers post-answer / post-timer
                            // states on the live frame.
                            locked = !live || !canAnswer || myAnswer != null || timeUp,
                            isMine = isMyChoice,
                            revealCorrect = showCorrect,
                            revealMineWrong = showIncorrectMine,
                            onClick = {
                                if (!live || !canAnswer || myAnswer != null || timeUp) {
                                    return@ChoiceButton
                                }
                                // Fire-and-forget. The realtime feed
                                // echoes our row back via `answers`;
                                // the (question_id, client_id) UNIQUE
                                // makes any retry idempotent.
                                answerScope.launch {
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
                            },
                        )
                    }
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
                Text(
                    TriviaPalette.symbols[index],
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
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
                    Text("\u2713", color = baseBg, fontWeight = FontWeight.Bold)
                }
            } else if (isMine) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "\u25CB",
                        color = baseBg,
                        fontWeight = FontWeight.Bold,
                    )
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

