package com.xergioalex.kmppttdynamics.trivia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Locks the routing contract used by [TriviaTab][com.xergioalex.kmppttdynamics.ui.room.tabs.TriviaTab]:
 * `board.live` must surface exactly the in-progress / calculating
 * quiz so the tab can preempt the list view with a full-screen
 * takeover. Drafts, lobbies and finished quizzes never count as
 * "live".
 */
class TriviaBoardTest {

    private val anyInstant = Instant.fromEpochMilliseconds(0)

    private fun quiz(id: String, status: TriviaStatus): TriviaQuiz = TriviaQuiz(
        id = id,
        meetupId = "m1",
        title = "Round $id",
        status = status,
        defaultSecondsPerQuestion = 15,
        createdAt = anyInstant,
    )

    @Test
    fun emptyBoardHasNoLiveQuiz() {
        val board = TriviaBoard(emptyList(), emptyMap(), emptyMap())
        assertNull(board.live)
    }

    @Test
    fun draftAndLobbyAndFinishedAreNeverLive() {
        val board = TriviaBoard(
            quizzes = listOf(
                quiz("a", TriviaStatus.DRAFT),
                quiz("b", TriviaStatus.LOBBY),
                quiz("c", TriviaStatus.FINISHED),
            ),
            questionsByQuiz = emptyMap(),
            choicesByQuestion = emptyMap(),
        )
        assertNull(board.live)
    }

    @Test
    fun inProgressQuizIsLive() {
        val live = quiz("a", TriviaStatus.IN_PROGRESS)
        val board = TriviaBoard(
            quizzes = listOf(quiz("draft", TriviaStatus.DRAFT), live),
            questionsByQuiz = emptyMap(),
            choicesByQuestion = emptyMap(),
        )
        assertEquals(live, board.live)
    }

    @Test
    fun calculatingQuizIsLive() {
        val live = quiz("a", TriviaStatus.CALCULATING)
        val board = TriviaBoard(
            quizzes = listOf(live, quiz("b", TriviaStatus.LOBBY)),
            questionsByQuiz = emptyMap(),
            choicesByQuestion = emptyMap(),
        )
        assertEquals(live, board.live)
    }

    @Test
    fun whenMultipleAreLiveTheFirstWins() {
        // Logically only one trivia can be live at a time (the host
        // can't run two question screens simultaneously), but the
        // schema doesn't enforce it, so the routing rule is "first by
        // list order". The repository sorts quizzes by created_at DESC
        // so that's the most recently created one.
        val newer = quiz("newer", TriviaStatus.IN_PROGRESS)
        val older = quiz("older", TriviaStatus.CALCULATING)
        val board = TriviaBoard(
            quizzes = listOf(newer, older),
            questionsByQuiz = emptyMap(),
            choicesByQuestion = emptyMap(),
        )
        assertEquals(newer, board.live)
    }
}
