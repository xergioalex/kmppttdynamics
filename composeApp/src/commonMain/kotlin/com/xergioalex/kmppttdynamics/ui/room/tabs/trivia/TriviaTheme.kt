package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Game-asset palette and timing constants for the Kahoot-style trivia
 * tab.
 *
 * The four `Choice*` colors are intentionally hardcoded — they're not
 * part of Material 3 theming, they're the game's identity, like a
 * sprite color in a video game. Using the project's `colorScheme.*`
 * tokens here would make the four buttons indistinguishable across
 * themes (e.g. `secondary` and `tertiary` collapse to similar greens
 * in some palettes), which would defeat the whole "tap the colored
 * button" mechanic. We compensate by always pairing each color with
 * a high-contrast on-color and a distinctive icon shape for
 * accessibility (red→triangle, blue→diamond, yellow→circle,
 * green→square — same as Kahoot).
 */
@Immutable
object TriviaPalette {
    val Red    = Color(0xFFE21B3C)
    val Blue   = Color(0xFF1368CE)
    val Yellow = Color(0xFFFFA602)
    val Green  = Color(0xFF26890C)

    val OnRed    = Color.White
    val OnBlue   = Color.White
    val OnYellow = Color(0xFF1B1B1B)
    val OnGreen  = Color.White

    /** Indexed view used by the question / setup screens. */
    val backgrounds = listOf(Red, Blue, Yellow, Green)
    val foregrounds = listOf(OnRed, OnBlue, OnYellow, OnGreen)

    /** Unicode badges that double as the iconography. */
    val symbols = listOf("\u25B2", "\u25C6", "\u25CF", "\u25A0")
}

/** Configurable durations centralized so the visual feel can be tuned in one place. */
@Immutable
object TriviaTiming {
    /** How long the answer-reveal overlay sits on screen between questions. */
    const val ANSWER_REVEAL_MS: Int = 1_800

    /** Duration of the suspense screen between the last answer and the leaderboard. */
    const val CALCULATING_MS: Int = 10_000

    /** The countdown ring goes red and pulses when this many seconds remain. */
    const val LAST_SECONDS_PULSE: Int = 3
}
