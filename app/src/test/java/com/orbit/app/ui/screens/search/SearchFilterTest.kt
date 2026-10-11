package com.orbit.app.ui.screens.search

import androidx.compose.ui.graphics.Color
import com.orbit.app.domain.search.LocalSearchResult
import com.orbit.app.domain.search.LocalSearchStatus
import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {
    private fun result(type: ItemDetailType, status: LocalSearchStatus) =
        LocalSearchResult(type, 1L, "Garden plan", "", null, status, 0L)

    @Test
    fun filtersKeepOnlyTheirKind_andArchivedOnlyArchived() {
        val task = result(ItemDetailType.Task, LocalSearchStatus.Task)
        val archivedTask = result(ItemDetailType.Task, LocalSearchStatus.Archived)
        val note = result(ItemDetailType.Note, LocalSearchStatus.Note)

        assertTrue(task.matches(SearchFilter.All))
        assertFalse(archivedTask.matches(SearchFilter.All))
        assertTrue(task.matches(SearchFilter.Tasks))
        assertFalse(note.matches(SearchFilter.Tasks))
        assertTrue(note.matches(SearchFilter.Notes))
        assertTrue(archivedTask.matches(SearchFilter.Archived))
        assertFalse(task.matches(SearchFilter.Archived))
    }

    @Test
    fun theTypedWordIsMarkedWhereverItAppears_ignoringCase() {
        val marked = "Garden plan for the garden".withMatch("garden", Color.Red)

        assertEquals(listOf(0 to 6, 20 to 26), marked.spanStyles.map { it.start to it.end })
        assertTrue("Garden".withMatch("g", Color.Red).spanStyles.isEmpty())
    }

    @Test
    fun unsortedThoughtsAreTheirOwnGroupAfterFinishedItems() {
        val thought = result(ItemDetailType.Capture, LocalSearchStatus.Note)
        val note = result(ItemDetailType.Note, LocalSearchStatus.Note)
        val archived = result(ItemDetailType.Note, LocalSearchStatus.Archived)

        assertEquals(SearchGroup.ToSort, thought.groupKey())
        val order = listOf(thought, archived, note).map { it.groupKey() }.sortedBy { it.ordinal }
        assertEquals(listOf(SearchGroup.Notes, SearchGroup.ToSort, SearchGroup.Archived), order)
    }
}
