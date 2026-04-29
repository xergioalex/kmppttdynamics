package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xergioalex.kmppttdynamics.trivia.TriviaQuiz
import kmppttdynamics.composeapp.generated.resources.Res
import kmppttdynamics.composeapp.generated.resources.trivia_calculating
import kmppttdynamics.composeapp.generated.resources.trivia_calculating_helper
import kotlin.time.Clock
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * 10 s suspense screen between the last answer and the leaderboard.
 *
 * Every device runs the timer locally from
 * [TriviaQuiz.calculatingStartedAt]. Whichever client's coroutine
 * fires `onFinish` first wins; the WHERE-guarded UPDATE in the
 * repository turns later fires into no-ops. Result: even if 50
 * devices race, only one UPDATE actually changes the row.
 *
 * Animation: three breathing dots that stagger in size, plus a slow
 * pulsing background "shimmer" so the screen feels alive instead of
 * frozen during the 10 s wait.
 */
@Composable
fun CalculatingScreen(
    quiz: TriviaQuiz,
    onFinish: () -> Unit,
) {
    val startedAt = quiz.calculatingStartedAt
    LaunchedEffect(quiz.id, startedAt) {
        if (startedAt == null) return@LaunchedEffect
        val now = Clock.System.now().toEpochMilliseconds()
        val deadline = startedAt.toEpochMilliseconds() + TriviaTiming.CALCULATING_MS
        val waitMs = (deadline - now).coerceAtLeast(0L)
        delay(waitMs)
        onFinish()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BreathingHalo()
        Spacer(Modifier.height(24.dp))
        BreathingDots()
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(Res.string.trivia_calculating),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.trivia_calculating_helper),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BreathingHalo() {
    val transition = rememberInfiniteTransition(label = "trivia-calc-halo")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trivia-calc-halo-scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trivia-calc-halo-alpha",
    )
    Box(
        modifier = Modifier.size(160.dp).scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "\u2728",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Three dots that bounce in stagger. The outer transition drives a
 * single phase; each dot computes its own scale by offsetting that
 * phase, so they breathe at the same rate but out of sync.
 */
@Composable
private fun BreathingDots() {
    val transition = rememberInfiniteTransition(label = "trivia-calc-dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "trivia-calc-dots-phase",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(3) { idx ->
            val offset = (idx * 0.18f)
            val raw = ((phase + offset) % 1f)
            // Triangle wave — peak at 0.5, trough at 0/1.
            val wave = if (raw < 0.5f) raw * 2f else (1f - raw) * 2f
            val scale = 0.7f + 0.5f * wave
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(18.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
    }
}
