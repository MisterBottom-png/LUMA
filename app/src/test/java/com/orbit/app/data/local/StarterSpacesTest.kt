package com.orbit.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterSpacesTest {
    @Test
    fun starterSpacesHaveStableUniqueIdsNamesAndSortOrder() {
        val spaces = StarterSpaces.entities(createdAt = 123L)

        assertEquals(9, spaces.size)
        assertEquals(StarterSpaces.names, spaces.map { it.name })
        assertEquals((1L..9L).toList(), spaces.map { it.id })
        assertEquals((0..8).toList(), spaces.map { it.sortOrder })
        assertEquals(spaces.size, spaces.map { it.name }.toSet().size)
        assertTrue(spaces.all { it.createdAt == 123L && it.updatedAt == 123L })
    }
}
