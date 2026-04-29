package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.trivia.TriviaLeaderboardEntry
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import com.xergioalex.kmppttdynamics.ui.components.AvatarImage
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.trivia_celebration_below_message
import kmppttdynamics.composeapp.generated.resources.trivia_celebration_below_title
import kmppttdynamics.composeapp.generated.resources.trivia_celebration_continue
import kmppttdynamics.composeapp.generated.resources.trivia_celebration_podium_message
import kmppttdynamics.composeapp.generated.resources.trivia_celebration_podium_message_top
import kmppttdynamics.composeapp.generated.resources.trivia_celebration_podium_title
import kmppttdynamics.composeapp.generated.resources.trivia_celebration_spectator_message
import kmppttdynamics.composeapp.generated.resources.trivia_celebration_spectator_title
import kmppttdynamics.composeapp.generated.resources.trivia_leaderboard_correct
import kmppttdynamics.composeapp.generated.resources.trivia_leaderboard_no_one
import kmppttdynamics.composeapp.generated.resources.trivia_leaderboard_title
import kmppttdynamics.composeapp.generated.resources.trivia_leaderboard_you
import kmppttdynamics.composeapp.generated.resources.trivia_new_round
import kmppttdynamics.composeapp.generated.resources.trivia_play_again
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import org.jetbrains.compose.resources.stringResource

/**
 * Final leaderboard screen.
 *
 * Two phases:
 *
 *  - **Celebration** (only when [celebrate] = true) — a personal "you
 *    finished at #X!" takeover, sized differently for podium ranks vs
 *    everyone-else, that auto-advances after [CELEBRATION_MS] ms or
 *    when the user taps "View leaderboard". Spectators (clients
 *    without a leaderboard entry) get a generic "trivia complete"
 *    variant. Skipped entirely when the screen is opened by the
 *    "View leaderboard" button on the trivia card list, so it only
 *    fires once per round.
 *  - **Leaderboard** — top 3 revealed podium-style (3rd → 2nd → 1st
 *    with a stagger delay so the host can sell the moment), winner
 *    gets a confetti overlay, ranks #4 onwards render in a scrollable
 *    list below the podium. The current user's pedestal / row is
 *    highlighted with a primary-colored ring + "You" chip.
 *
 * Sorting (`total_points DESC`, then `avg_response_ms ASC`, then
 * `display_name ASC`) is done server-side by
 * `TriviaRepository.fetchLeaderboard` — this composable trusts the
 * incoming order.
 */
@Composable
fun LeaderboardScreen(
    quiz: TriviaQuiz,
    container: AppContainer,
    isHost: Boolean,
    isWorking: Boolean,
    myClientId: String?,
    celebrate: Boolean,
    onPlayAgain: () -> Unit,
    onNewRound: () -> Unit,
) {
    var entries by remember(quiz.id) { mutableStateOf<List<TriviaLeaderboardEntry>>(emptyList()) }
    LaunchedEffect(quiz.id) {
        container.trivia.observeLeaderboard(quiz.id)
            .catch { /* non-fatal */ }
            .collect { entries = it }
    }

    // Personal celebration phase: only when this screen was opened
    // automatically right after CALCULATING -> FINISHED. Once the
    // user is in the regular leaderboard (or arrived via the manual
    // "View leaderboard" button) we never show it again.
    var showCelebration by remember(quiz.id, celebrate) { mutableStateOf(celebrate) }

    AnimatedContent(
        targetState = showCelebration,
        transitionSpec = {
            (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.92f))
                .togetherWith(fadeOut(tween(300)))
        },
        label = "trivia-leaderboard-phase",
    ) { celebrating ->
        if (celebrating) {
            CelebrationOverlay(
                entries = entries,
                myClientId = myClientId,
                onContinue = { showCelebration = false },
            )
        } else {
            LeaderboardContent(
                entries = entries,
                myClientId = myClientId,
                isHost = isHost,
                isWorking = isWorking,
                onPlayAgain = onPlayAgain,
                onNewRound = onNewRound,
            )
        }
    }
}

@Composable
private fun LeaderboardContent(
    entries: List<TriviaLeaderboardEntry>,
    myClientId: String?,
    isHost: Boolean,
    isWorking: Boolean,
    onPlayAgain: () -> Unit,
    onNewRound: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            stringResource(Res.string.trivia_leaderboard_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.trivia_leaderboard_no_one),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Podium(entries = entries.take(3), myClientId = myClientId)
                    Spacer(Modifier.height(16.dp))
                    if (entries.size > 3) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(entries.drop(3), key = { it.clientId }) { entry ->
                                RankRow(
                                    rank = entries.indexOf(entry) + 1,
                                    entry = entry,
                                    isMe = entry.clientId == myClientId,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isHost) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPlayAgain,
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.trivia_play_again)) }
                Button(
                    onClick = onNewRound,
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.trivia_new_round)) }
            }
        }
    }
}

/** How long the celebration overlay holds before auto-routing to the
 *  leaderboard. Tap "View leaderboard" to skip. */
private const val CELEBRATION_MS = 5_000L

/**
 * Personal pre-leaderboard takeover. Reads the current user's rank
 * from [entries] and tailors the message:
 *
 *  - Rank 1: "You took the top of the podium!"
 *  - Rank 2 / 3: "You reached the podium at #X!"
 *  - Rank 4+: "You finished at #X."
 *  - Spectator / no entry: generic "trivia complete".
 *
 * Auto-advances after [CELEBRATION_MS] ms; [onContinue] also fires on
 * tap so impatient users aren't held hostage by the animation.
 */
@Composable
private fun CelebrationOverlay(
    entries: List<TriviaLeaderboardEntry>,
    myClientId: String?,
    onContinue: () -> Unit,
) {
    val myIndex = remember(entries, myClientId) {
        if (myClientId == null) -1 else entries.indexOfFirst { it.clientId == myClientId }
    }
    val myEntry = entries.getOrNull(myIndex)
    val myRank = if (myIndex >= 0) myIndex + 1 else null

    LaunchedEffect(Unit) {
        delay(CELEBRATION_MS)
        onContinue()
    }

    val title: String
    val message: String
    when {
        myRank == 1 -> {
            title = stringResource(Res.string.trivia_celebration_podium_title)
            message = stringResource(Res.string.trivia_celebration_podium_message_top)
        }
        myRank == 2 || myRank == 3 -> {
            title = stringResource(Res.string.trivia_celebration_podium_title)
            message = stringResource(Res.string.trivia_celebration_podium_message, myRank)
        }
        myRank != null -> {
            title = stringResource(Res.string.trivia_celebration_below_title)
            message = stringResource(Res.string.trivia_celebration_below_message, myRank)
        }
        else -> {
            title = stringResource(Res.string.trivia_celebration_spectator_title)
            message = stringResource(Res.string.trivia_celebration_spectator_message)
        }
    }

    // Pulsating halo behind the avatar — same vibe as CalculatingScreen
    // so the two screens feel like a continuous animation.
    val transition = rememberInfiniteTransition(label = "trivia-celebration")
    val haloPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trivia-celebration-halo",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .clickable(onClick = onContinue),
        contentAlignment = Alignment.Center,
    ) {
        // Confetti only celebrates podium finishes; mid-pack ranks
        // and spectators get a calmer screen.
        if (myRank != null && myRank <= 3) ConfettiOverlay()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Halo
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .graphicsLayer {
                            scaleX = 0.85f + haloPhase * 0.25f
                            scaleY = 0.85f + haloPhase * 0.25f
                            alpha = 0.20f + haloPhase * 0.30f
                        }
                        .clip(CircleShape)
                        .background(
                            if (myRank != null && myRank <= 3) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                        ),
                )
                if (myEntry?.avatarId != null) {
                    AvatarImage(avatarId = myEntry.avatarId, size = 120.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                if (myRank != null) {
                    // Floating rank badge in the bottom-right of the avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { translationX = 50.dp.toPx(); translationY = 40.dp.toPx() }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "#$myRank",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            myEntry?.let {
                Text(
                    "${it.totalPoints} pts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onContinue) {
                Text(stringResource(Res.string.trivia_celebration_continue))
            }
        }
    }
}

/**
 * Podium for the top 3. Visual order is 2nd, 1st (taller), 3rd; reveal
 * order is 3rd → 2nd → 1st with 700 ms between each. Empty slots fill
 * with placeholder cards so the layout doesn't shift when the round
 * has fewer than 3 ranked players. The pedestal whose entry matches
 * [myClientId] gets a primary-colored ring + "You" chip so the user
 * can spot themselves at a glance.
 */
@Composable
private fun Podium(entries: List<TriviaLeaderboardEntry>, myClientId: String?) {
    val first = entries.getOrNull(0)
    val second = entries.getOrNull(1)
    val third = entries.getOrNull(2)

    val secondReveal = remember(first?.clientId) { Animatable(0f) }
    val firstReveal = remember(first?.clientId) { Animatable(0f) }
    val thirdReveal = remember(first?.clientId) { Animatable(0f) }

    LaunchedEffect(first?.clientId) {
        // 3rd, 2nd, 1st — same cadence Kahoot uses.
        thirdReveal.animateTo(1f, tween(durationMillis = 600))
        delay(150)
        secondReveal.animateTo(1f, tween(durationMillis = 600))
        delay(150)
        firstReveal.animateTo(1f, tween(durationMillis = 800))
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Confetti only on the winner area; drawn behind the cards so
        // the cards stay readable.
        if (first != null) ConfettiOverlay()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            PodiumPedestal(
                rank = 2,
                entry = second,
                heightDp = 120,
                tone = MaterialTheme.colorScheme.secondary,
                onTone = MaterialTheme.colorScheme.onSecondary,
                appearance = secondReveal.value,
                isMe = second != null && second.clientId == myClientId,
                modifier = Modifier.weight(1f),
            )
            PodiumPedestal(
                rank = 1,
                entry = first,
                heightDp = 160,
                tone = MaterialTheme.colorScheme.primary,
                onTone = MaterialTheme.colorScheme.onPrimary,
                appearance = firstReveal.value,
                isMe = first != null && first.clientId == myClientId,
                modifier = Modifier.weight(1f),
            )
            PodiumPedestal(
                rank = 3,
                entry = third,
                heightDp = 95,
                tone = MaterialTheme.colorScheme.tertiary,
                onTone = MaterialTheme.colorScheme.onTertiary,
                appearance = thirdReveal.value,
                isMe = third != null && third.clientId == myClientId,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PodiumPedestal(
    rank: Int,
    entry: TriviaLeaderboardEntry?,
    heightDp: Int,
    tone: Color,
    onTone: Color,
    appearance: Float,
    isMe: Boolean,
    modifier: Modifier = Modifier,
) {
    val safeAppearance = appearance.coerceIn(0f, 1f)
    val highlightColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.graphicsLayer {
            alpha = safeAppearance
            scaleX = 0.85f + 0.15f * safeAppearance
            scaleY = 0.85f + 0.15f * safeAppearance
            translationY = (1f - safeAppearance) * 30.dp.toPx()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val avatarModifier = if (isMe) {
            Modifier.border(width = 3.dp, color = highlightColor, shape = CircleShape)
        } else {
            Modifier
        }
        if (entry?.avatarId != null) {
            Box(modifier = avatarModifier.clip(CircleShape)) {
                AvatarImage(avatarId = entry.avatarId, size = 64.dp)
            }
        } else {
            Box(
                modifier = avatarModifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry?.displayName ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (isMe) {
            YouChip()
            Spacer(Modifier.height(2.dp))
        }
        Text(
            "${entry?.totalPoints ?: 0} pts",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(tone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "#$rank",
                color = onTone,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun YouChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            stringResource(Res.string.trivia_leaderboard_you),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RankRow(rank: Int, entry: TriviaLeaderboardEntry, isMe: Boolean) {
    val cardColors = if (isMe) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
    } else {
        CardDefaults.cardColors()
    }
    val rowModifier = if (isMe) {
        Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
            )
    } else {
        Modifier.fillMaxWidth()
    }
    Card(
        modifier = rowModifier,
        colors = cardColors,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "#$rank",
                color = if (isMe) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
            val avatarBorder = if (isMe) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            } else {
                Modifier
            }
            if (entry.avatarId != null) {
                Box(modifier = avatarBorder.clip(CircleShape)) {
                    AvatarImage(avatarId = entry.avatarId, size = 36.dp)
                }
            } else {
                Box(
                    modifier = avatarBorder
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMe) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (isMe) {
                        Spacer(Modifier.size(8.dp))
                        YouChip()
                    }
                }
                Text(
                    stringResource(Res.string.trivia_leaderboard_correct, entry.correctCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMe) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                "${entry.totalPoints} pts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Particle confetti drawn behind the podium. We sample 30 particles
 * with random offsets/colors and animate a global phase float; each
 * particle's vertical position is `phase * speed_i + offset_i`, so
 * the confetti continuously rains down for as long as the screen is
 * visible (cheap to compute, and a fixed seed keeps the look stable
 * across recompositions).
 */
@Composable
private fun ConfettiOverlay() {
    val transition = rememberInfiniteTransition(label = "trivia-confetti")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_500),
            repeatMode = RepeatMode.Restart,
        ),
        label = "trivia-confetti-phase",
    )
    val particles = remember {
        // Fixed seed so the same constellation renders across
        // recompositions instead of jittering frame-to-frame.
        val rng = Random(42)
        List(36) {
            Particle(
                xFraction = rng.nextFloat(),
                phaseOffset = rng.nextFloat(),
                speed = 0.6f + rng.nextFloat() * 0.6f,
                color = TriviaPalette.backgrounds.random(rng),
                wobble = rng.nextFloat(),
                size = 5f + rng.nextFloat() * 6f,
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .drawBehind {
                particles.forEach { p ->
                    val travel = (phase * p.speed + p.phaseOffset) % 1f
                    // sin(...) returns Double, so we add the wobble in
                    // double-space and cast once at the end. y is
                    // Float * Float = Float and stays as is.
                    val x = (p.xFraction * size.width +
                        (sin(travel * 2 * PI + p.wobble * 6) * 8.0).toFloat())
                    val y = travel * size.height
                    drawCircle(
                        color = p.color,
                        radius = p.size,
                        center = Offset(x, y),
                    )
                }
            },
    )
}

private data class Particle(
    val xFraction: Float,
    val phaseOffset: Float,
    val speed: Float,
    val color: Color,
    val wobble: Float,
    val size: Float,
)
