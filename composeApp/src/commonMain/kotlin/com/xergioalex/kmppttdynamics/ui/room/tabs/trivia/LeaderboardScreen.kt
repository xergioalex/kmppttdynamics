package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.AppContainer
import com.xergioalex.kmppttdynamics.trivia.TriviaLeaderboardEntry
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import com.xergioalex.kmppttdynamics.ui.components.AvatarImage
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.trivia_leaderboard_correct
import kmppttdynamics.composeapp.generated.resources.trivia_leaderboard_no_one
import kmppttdynamics.composeapp.generated.resources.trivia_leaderboard_title
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
 * - Top 3 are revealed in podium form (3rd, then 2nd, then 1st) with a
 *   stagger delay so the host can sell the moment.
 * - The winner row gets a confetti overlay (custom particle field
 *   drawn behind the podium) for ~6 s.
 * - Below the podium: a regular ranked list of every other player.
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
    onPlayAgain: () -> Unit,
    onNewRound: () -> Unit,
) {
    var entries by remember(quiz.id) { mutableStateOf<List<TriviaLeaderboardEntry>>(emptyList()) }
    LaunchedEffect(quiz.id) {
        container.trivia.observeLeaderboard(quiz.id)
            .catch { /* non-fatal */ }
            .collect { entries = it }
    }

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
                    Podium(entries = entries.take(3))
                    Spacer(Modifier.height(16.dp))
                    if (entries.size > 3) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(entries.drop(3), key = { it.clientId }) { entry ->
                                RankRow(
                                    rank = entries.indexOf(entry) + 1,
                                    entry = entry,
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

/**
 * Podium for the top 3. Visual order is 2nd, 1st (taller), 3rd; reveal
 * order is 3rd → 2nd → 1st with 700 ms between each. Empty slots fill
 * with placeholder cards so the layout doesn't shift when the round
 * has fewer than 3 ranked players.
 */
@Composable
private fun Podium(entries: List<TriviaLeaderboardEntry>) {
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
                modifier = Modifier.weight(1f),
            )
            PodiumPedestal(
                rank = 1,
                entry = first,
                heightDp = 160,
                tone = MaterialTheme.colorScheme.primary,
                onTone = MaterialTheme.colorScheme.onPrimary,
                appearance = firstReveal.value,
                modifier = Modifier.weight(1f),
            )
            PodiumPedestal(
                rank = 3,
                entry = third,
                heightDp = 95,
                tone = MaterialTheme.colorScheme.tertiary,
                onTone = MaterialTheme.colorScheme.onTertiary,
                appearance = thirdReveal.value,
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
    modifier: Modifier = Modifier,
) {
    val safeAppearance = appearance.coerceIn(0f, 1f)
    Column(
        modifier = modifier.graphicsLayer {
            alpha = safeAppearance
            scaleX = 0.85f + 0.15f * safeAppearance
            scaleY = 0.85f + 0.15f * safeAppearance
            translationY = (1f - safeAppearance) * 30.dp.toPx()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (entry?.avatarId != null) {
            AvatarImage(avatarId = entry.avatarId, size = 64.dp)
        } else {
            Box(
                modifier = Modifier
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
private fun RankRow(rank: Int, entry: TriviaLeaderboardEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "#$rank",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
            if (entry.avatarId != null) {
                AvatarImage(avatarId = entry.avatarId, size = 36.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(Res.string.trivia_leaderboard_correct, entry.correctCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
