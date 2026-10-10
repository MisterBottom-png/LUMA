package com.orbit.app.ui.screens.situation

import com.orbit.app.domain.ai.SourceLinkedAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SituationAiAskStateTest {
    private val answer = SourceLinkedAnswer(
        answer = "A current answer",
        sourceItemIds = emptyList(),
        sourceItems = emptyList(),
        fromGemini = false,
    )

    @Test
    fun meaningfulEditClearsDisplayedAnswerWhileWhitespaceOnlyEditKeepsIt() {
        val answered = completedState("What is next?")

        val whitespaceEdit = answered.withQuery("  What   is next?  ")
        val meaningfulEdit = whitespaceEdit.withQuery("What is blocked?")

        assertSame(answer, whitespaceEdit.answer)
        assertNull(meaningfulEdit.answer)
    }

    @Test
    fun submissionClearsPreviousAnswerAndEntersLoadingState() {
        val submission = completedState("What is next?").beginSubmission()

        requireNotNull(submission)
        assertEquals("What is next?", submission.question)
        assertTrue(submission.loadingState.isAsking)
        assertNull(submission.loadingState.answer)
    }

    @Test
    fun completionDisplaysAnswerOnlyWhenQuestionIsStillCurrent() {
        val submission = AskState(query = "What is next?").beginSubmission()
        requireNotNull(submission)

        val current = submission.loadingState.completeSubmission(submission.question, answer)
        val edited = submission.loadingState
            .withQuery("What is blocked?")
            .completeSubmission(submission.question, answer)

        assertFalse(current.isAsking)
        assertSame(answer, current.answer)
        assertFalse(edited.isAsking)
        assertNull(edited.answer)
    }

    @Test
    fun failedSubmissionReturnsToIdleWithoutRestoringClearedAnswer() {
        val submission = completedState("What is next?").beginSubmission()
        requireNotNull(submission)

        val idle = submission.loadingState.finishSubmission(submission.question)

        assertFalse(idle.isAsking)
        assertNull(idle.answer)
    }

    @Test
    fun displayedAnswerIsInvalidatedWhenLocalDataChanges() {
        val submission = AskState(query = "What is next?").beginSubmission()
        requireNotNull(submission)
        val answered = submission.loadingState.completeSubmission(submission.question, answer, dataKey = 10)

        assertSame(answer, answered.answerFor(10))
        assertNull(answered.answerFor(11))
    }

    private fun completedState(question: String): AskState {
        val submission = AskState(query = question).beginSubmission()
        requireNotNull(submission)
        return submission.loadingState.completeSubmission(submission.question, answer)
    }
}
