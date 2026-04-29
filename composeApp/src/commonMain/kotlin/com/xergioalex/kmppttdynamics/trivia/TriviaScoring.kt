package com.xergioalex.kmppttdynamics.trivia

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure Kotlin mirror of the `trivia_compute_answer_score` Postgres
 * trigger. The trigger is the **runtime source of truth** for scoring
 * — this object exists so:
 *
 *   1. The formula can be unit-tested without spinning up a database.
 *   2. The UI can render an *optimistic* "+N pts" banner before the
 *      `trivia_answers` realtime row echoes back with the
 *      server-computed `pointsAwarded` (we don't currently use this,
 *      but it's a one-line swap if we ever want zero-latency feedback).
 *   3. Future work that moves the scoring into an Edge Function or
 *      `pg_cron` job can keep parity with the original formula by
 *      diffing against this object.
 *
 * **Do NOT** start writing client-computed scores into
 * `trivia_answers` — the trigger overwrites those fields on insert,
 * so a tampered client payload is silently ignored. Anti-cheat is
 * the whole reason scoring lives server-side.
 */
object TriviaScoring {

    /** Floor awarded for a correct answer, regardless of speed. */
    const val MIN_CORRECT_POINTS: Int = 500

    /** Bonus available on top of [MIN_CORRECT_POINTS] for an
     *  instantaneous correct answer. */
    const val SPEED_BONUS_POINTS: Int = 500

    /**
     * Computes the points awarded for a single answer, mirroring the
     * Kahoot formula used in the Postgres trigger.
     *
     * @param isCorrect      whether the chosen choice is the correct one
     * @param responseMs     elapsed milliseconds between the question
     *                       starting and the answer landing on the
     *                       server. Negative values are clamped to 0;
     *                       values above the question window are
     *                       clamped to the window (matches the
     *                       trigger's `greatest(...)` and clamp-to-cap
     *                       guards).
     * @param windowSeconds  total seconds the question was available.
     *                       Must be > 0; values <= 0 fall back to 15
     *                       (the application-wide default).
     */
    fun pointsFor(isCorrect: Boolean, responseMs: Int, windowSeconds: Int): Int {
        if (!isCorrect) return 0
        val safeWindow = if (windowSeconds <= 0) 15 else windowSeconds
        val totalMs = (safeWindow * 1000).toDouble()
        val clamped = responseMs.coerceIn(0, safeWindow * 1000).toDouble()
        val fractionRemaining = 1.0 - (clamped / totalMs)
        val raw = MIN_CORRECT_POINTS + SPEED_BONUS_POINTS * fractionRemaining
        return raw.roundToInt()
    }

    /**
     * Computes how many milliseconds elapsed between the question
     * starting and an answer landing, mirroring the trigger's
     * "late-insert guard": if the answer is for a question whose
     * `position` doesn't match the quiz's `current_question_index`
     * any longer, the elapsed time is clamped to the full window so a
     * network race can't gift the +1 000 instant-bonus.
     *
     * @param now                "current" time in epoch ms (from
     *                           `Clock.System.now().toEpochMilliseconds()`)
     * @param questionStartedAt  when the active question started, in
     *                           epoch ms. Null is treated as "now",
     *                           which matches the trigger's null-safe
     *                           fallback for a quiz that's mid-state-
     *                           transition.
     * @param windowSeconds      question window in seconds (used only
     *                           for the upper clamp).
     * @param questionIsCurrent  whether `q.position == tq.current_question_index`
     *                           at the moment the trigger evaluated.
     *                           When false the elapsed value is
     *                           pinned to the window cap.
     */
    fun elapsedMs(
        now: Long,
        questionStartedAt: Long?,
        windowSeconds: Int,
        questionIsCurrent: Boolean = true,
    ): Int {
        val safeWindow = if (windowSeconds <= 0) 15 else windowSeconds
        val cap = safeWindow * 1000
        if (!questionIsCurrent) return cap
        val started = questionStartedAt ?: now
        return (now - started).coerceIn(0L, cap.toLong()).toInt()
    }

    /**
     * Mirror of the trigger's per-type correctness check for
     * [TriviaQuestionType.MULTIPLE]. The submitted set is correct iff
     * it has the *exact* same elements as the canonical correct set —
     * no missing picks, no extras. Order doesn't matter.
     *
     * Edge cases that match the Postgres trigger:
     *   - Empty submitted set against a non-empty correct set → wrong.
     *   - Empty submitted AND empty correct set → wrong (a question
     *     with zero correct choices is malformed; the trigger
     *     intentionally returns false to surface the bug instead of
     *     silently scoring everyone correct).
     */
    fun isMultipleCorrect(submitted: Collection<String>, correct: Collection<String>): Boolean {
        if (correct.isEmpty()) return false
        return submitted.toSet() == correct.toSet()
    }

    /**
     * Mirror of the trigger's per-type correctness check for
     * [TriviaQuestionType.NUMERIC]. Correct iff the submitted value
     * is within `[expected ± tolerance]`.
     *
     *   - Null `expected` (malformed question) → never correct.
     *   - Null `submitted` (empty input) → never correct.
     *   - Negative tolerance is treated as 0 (matches the trigger's
     *     `coalesce(q_tolerance, 0)` plus a NOT NULL DEFAULT 0 on the
     *     column — this client guard catches a future regression if
     *     the column constraint is ever loosened).
     */
    fun isNumericCorrect(submitted: Double?, expected: Double?, tolerance: Double): Boolean {
        if (expected == null || submitted == null) return false
        val safeTolerance = if (tolerance < 0) 0.0 else tolerance
        return abs(submitted - expected) <= safeTolerance
    }
}
