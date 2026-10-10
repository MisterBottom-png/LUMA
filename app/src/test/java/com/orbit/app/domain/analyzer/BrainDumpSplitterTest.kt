package com.orbit.app.domain.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainDumpSplitterTest {
    @Test
    fun numbersBulletsAndCheckboxesComeOffTheStartOfALine() {
        val fragments = BrainDumpSplitter.lineFragments("1. call bank\n2) buy milk\n• walk dog\n[ ] pay rent\n– water plants")

        assertEquals(listOf("call bank", "buy milk", "walk dog", "pay rent", "water plants"), fragments.map { it.title })
    }

    @Test
    fun aLinkAloneOnALineStaysWithTheTitleAboveIt() {
        val fragments = BrainDumpSplitter.lineFragments("Nice article about sleep\nhttps://example.com/sleep\nCall the garage")

        assertEquals(2, fragments.size)
        assertEquals("Nice article about sleep", fragments[0].title)
        assertEquals("Nice article about sleep\nhttps://example.com/sleep", fragments[0].text)
        assertEquals("Call the garage", fragments[1].title)
    }

    @Test
    fun aSharedTitleAndLinkIsOneThought_soItIsNotADump() {
        assertTrue(BrainDumpSplitter.lineFragments("Nice article\nhttps://example.com/a").isEmpty())
    }

    @Test
    fun aHeadingKeepsItsListAndCanBeSplitLater() {
        val fragments = BrainDumpSplitter.lineFragments("Shopping:\n- milk\n- eggs\nCall the dentist")

        assertEquals(listOf("Shopping", "Call the dentist"), fragments.map { it.title })
        assertEquals(listOf("milk", "eggs"), BrainDumpSplitter.listItemsOf(fragments[0].text))
    }

    @Test
    fun oneLineSplitsOnlyOnSafeSigns() {
        val actions = { part: String -> part.lowercase().startsWith("call") || part.lowercase().startsWith("buy") }

        assertEquals(listOf("call bank", "buy milk"), BrainDumpSplitter.oneLineParts("call bank; buy milk", actions))
        assertEquals(listOf("call bank", "buy milk"), BrainDumpSplitter.oneLineParts("1) call bank 2) buy milk", actions))
        assertEquals(listOf("Call bank", "Buy milk"), BrainDumpSplitter.oneLineParts("Call bank. Buy milk.", actions))
        // Never on "and" or commas: this is one thought.
        assertTrue(BrainDumpSplitter.oneLineParts("buy salt and pepper, and bread", actions).isEmpty())
        // Sentences that are not each an action stay together.
        assertTrue(BrainDumpSplitter.oneLineParts("Call bank. It closes early.", actions).isEmpty())
    }

    @Test
    fun geminiPartsMustBeExactCopiesInOrder() {
        val text = "buy dog food, call the garage and book the dentist"

        assertTrue(BrainDumpSplitter.partsAreExactCopies(text, listOf("buy dog food", "call the garage", "book the dentist")))
        assertFalse(BrainDumpSplitter.partsAreExactCopies(text, listOf("Buy dog food", "call the garage", "book the dentist")))
        assertFalse(BrainDumpSplitter.partsAreExactCopies(text, listOf("call the garage", "buy dog food", "book the dentist")))
        assertFalse(BrainDumpSplitter.partsAreExactCopies(text, listOf("buy dog food", "book the dentist")))
        assertFalse(BrainDumpSplitter.partsAreExactCopies(text, listOf(text)))
    }
}
