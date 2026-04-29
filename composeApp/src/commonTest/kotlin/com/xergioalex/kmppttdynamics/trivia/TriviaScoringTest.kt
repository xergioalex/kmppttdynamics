package com.xergioalex.kmppttdynamics.trivia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the Kahoot scoring formula: 0 pts for a wrong answer, 500..1000
 * for a correct one (linear interpolation over the question window).
 *
 * The runtime source of truth is the `trivia_compute_answer_score`
 * Postgres trigger; this Kotlin mirror is documented in
 * [TriviaScoring]. Both implementations must agree, so this test
 * doubles as a parity contract — if the formula changes in either
 * place, this test catches the drift before the leaderboard does.
 */
class TriviaScoringTest {

    @Test
    fun wrongAnswerScoresZeroRegardlessOfSpeed() {
        assertEquals(0, TriviaScoring.pointsFor(isCorrect = false, responseMs = 0, windowSeconds = 15))
        assertEquals(0, TriviaScoring.pointsFor(isCorrect = false, responseMs = 14_999, windowSeconds = 15))
    }

    @Test
    fun instantaneousCorrectAnswerHitsMaxBonus() {
        // 0 ms elapsed = full speed bonus = 1 000 pts.
        assertEquals(1000, TriviaScoring.pointsFor(isCorrect = true, responseMs = 0, windowSeconds = 15))
    }

    @Test
    fun lastSecondCorrectAnswerCollapsesToFloor() {
        // At the very end of the window, fraction_remaining ~= 0 so
        // the score collapses to the 500-point floor.
        assertEquals(500, TriviaScoring.pointsFor(isCorrect = true, responseMs = 15_000, windowSeconds = 15))
    }

    @Test
    fun midpointAnswerScoresHalfBonus() {
        // Halfway through the window: 500 floor + 250 bonus = 750.
        assertEquals(750, TriviaScoring.pointsFor(isCorrect = true, responseMs = 7_500, windowSeconds = 15))
    }

    @Test
    fun negativeElapsedClampsToZero() {
        // Clock skew on the client could send a negative; the trigger
        // clamps it, and so do we. Score should be the max bonus.
        assertEquals(1000, TriviaScoring.pointsFor(isCorrect = true, responseMs = -250, windowSeconds = 15))
    }

    @Test
    fun overshotElapsedClampsToWindowFloor() {
        // Network round-trip pushes the insert past the window: the
        // trigger clamps elapsed to the window cap, and so do we.
        assertEquals(500, TriviaScoring.pointsFor(isCorrect = true, responseMs = 60_000, windowSeconds = 15))
    }

    @Test
    fun nonPositiveWindowFallsBackTo15s() {
        // A misconfigured per-question seconds_to_answer of 0 mustn't
        // divide by zero — both the trigger and the helper fall back
        // to a 15 s window.
        val score = TriviaScoring.pointsFor(isCorrect = true, responseMs = 7_500, windowSeconds = 0)
        assertEquals(750, score)
    }

    @Test
    fun lateInsertAfterAdvanceCapsAtFloor() {
        // Reproduces the bug the trigger's "position vs
        // current_question_index" guard prevents: a tap that loses
        // the timer race lands when the host has already moved on,
        // and would otherwise be scored against the new question's
        // started_at (~0 ms old) for +1 000. The guard pins elapsed
        // to the cap, so the late insert gets the floor at best.
        val elapsed = TriviaScoring.elapsedMs(
            now = 100_000L,
            questionStartedAt = 99_500L,           // 500 ms "elapsed" naively
            windowSeconds = 15,
            questionIsCurrent = false,             // host already advanced
        )
        assertEquals(15_000, elapsed)
        assertEquals(
            500,
            TriviaScoring.pointsFor(isCorrect = true, responseMs = elapsed, windowSeconds = 15),
        )
    }

    @Test
    fun elapsedWithNullStartFallsBackToZero() {
        // Mid-state-transition the quiz row's
        // current_question_started_at can be null briefly; trigger
        // treats that as 0 ms elapsed — match.
        val elapsed = TriviaScoring.elapsedMs(
            now = 100_000L,
            questionStartedAt = null,
            windowSeconds = 15,
        )
        assertEquals(0, elapsed)
    }

    @Test
    fun scoresStayMonotonicallyDecreasingForCorrectAnswers() {
        // Property-style sanity check: faster correct answers should
        // never score lower than slower ones.
        var previous = Int.MAX_VALUE
        for (ms in 0..15_000 step 500) {
            val score = TriviaScoring.pointsFor(isCorrect = true, responseMs = ms, windowSeconds = 15)
            assertTrue(score <= previous, "expected non-increasing scores, got $score after $previous at $ms ms")
            previous = score
        }
    }
}
