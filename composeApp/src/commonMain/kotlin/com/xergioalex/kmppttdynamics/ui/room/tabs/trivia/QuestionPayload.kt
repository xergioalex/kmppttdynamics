package com.xergioalex.kmppttdynamics.ui.room.tabs.trivia

import com.xergioalex.kmppttdynamics.trivia.TriviaQuestionType

/**
 * Bundle of every editable field a host can author for a question.
 * Lives at the screen-level so the editor dialog can hand back one
 * payload that fits all four [TriviaQuestionType] variants instead
 * of a per-type lambda zoo.
 *
 * Field meaning per type:
 *
 *  - SINGLE / BOOLEAN — [labels] has 4 (single) or 2 (boolean)
 *    entries, [correctIndices] has exactly one. [expectedNumber] /
 *    [numericTolerance] are unused.
 *  - MULTIPLE — [labels] has 4 entries, [correctIndices] has 1+
 *    entries.
 *  - NUMERIC — [labels] / [correctIndices] are empty,
 *    [expectedNumber] is non-null, [numericTolerance] >= 0.
 */
data class QuestionPayload(
    val type: TriviaQuestionType,
    val prompt: String,
    val secondsToAnswer: Int,
    val labels: List<String> = emptyList(),
    val correctIndices: Set<Int> = emptySet(),
    val expectedNumber: Double? = null,
    val numericTolerance: Double = 0.0,
)
