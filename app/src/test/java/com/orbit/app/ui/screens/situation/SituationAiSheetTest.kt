package com.orbit.app.ui.screens.situation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SituationAiSheetTest {
    @Test
    fun sendIsEnabledOnlyForValidIdleQueries() {
        assertFalse(isAskSendEnabled(query = "", isAsking = false))
        assertFalse(isAskSendEnabled(query = " a ", isAsking = false))
        assertTrue(isAskSendEnabled(query = " ok ", isAsking = false))
        assertFalse(isAskSendEnabled(query = "ready", isAsking = true))
    }

    @Test
    fun composerShowsLoadingAndBlocksDuplicateSubmissionWhileAsking() {
        val loading = askComposerState(query = "ready", isAsking = true)

        assertTrue(loading.showLoading)
        assertFalse(loading.sendEnabled)
    }

    @Test
    fun composerRestoresSendControlAfterAskingFinishes() {
        val idle = askComposerState(query = "ready", isAsking = false)

        assertFalse(idle.showLoading)
        assertTrue(idle.sendEnabled)
    }

    @Test
    fun sharedSubmitPathRejectsBlankAndDuplicateRequests() {
        var submissions = 0

        submitAskIfEnabled(askComposerState(query = " ", isAsking = false)) { submissions += 1 }
        submitAskIfEnabled(askComposerState(query = "ready", isAsking = true)) { submissions += 1 }

        assertEquals(0, submissions)
    }

    @Test
    fun sharedSubmitPathInvokesValidRequestOnce() {
        var submissions = 0

        submitAskIfEnabled(askComposerState(query = "ready", isAsking = false)) { submissions += 1 }

        assertEquals(1, submissions)
    }
}

/** Opened from Ask, the sheet leads with the question and its answer; the overview follows. */
class SituationSheetOrderTest {
    @Test
    fun theQuestionBoxComesFirst_thenTheAnswer_thenTheOverview() {
        assertEquals(
            listOf(SituationSheetSection.Question, SituationSheetSection.Answer, SituationSheetSection.Overview),
            situationSheetSections(overviewReady = true, hasAnswer = true),
        )
    }

    @Test
    fun beforeAnyAnswer_theQuestionBoxStillLeads() {
        assertEquals(
            listOf(SituationSheetSection.Question, SituationSheetSection.Overview),
            situationSheetSections(overviewReady = true, hasAnswer = false),
        )
    }

    @Test
    fun whileTheOverviewLoads_theQuestionBoxIsAlreadyThere() {
        assertEquals(
            listOf(SituationSheetSection.Question, SituationSheetSection.Loading),
            situationSheetSections(overviewReady = false, hasAnswer = false),
        )
    }
}
