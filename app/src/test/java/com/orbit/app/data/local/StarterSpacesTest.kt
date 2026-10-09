package com.orbit.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterSpacesTest {
    @Test
    fun selectableTemplatesHaveStableKeysAndDistinctPresentation() {
        assertEquals(
            listOf("personal", "work", "home", "health", "money", "learning"),
            StarterSpaces.templates.map { it.key },
        )
        assertEquals(StarterSpaces.templates.size, StarterSpaces.templates.map { it.storedName }.toSet().size)
        assertEquals(StarterSpaces.templates.size, StarterSpaces.templates.map { it.icon }.toSet().size)
    }

    @Test
    fun selectedTemplateBuildsAnUnhiddenSpaceWithCallerOwnedOrder() {
        val space = StarterSpaces.spaceFor(
            template = StarterSpaces.templates.single { it.key == "home" },
            name = "Home",
            sortOrder = 4,
            now = 123L,
        )

        assertEquals(0L, space.id)
        assertEquals("Home", space.name)
        assertEquals("home", space.icon)
        assertEquals(4, space.sortOrder)
        assertTrue(!space.hidden && !space.archived)
        assertEquals(123L, space.createdAt)
        assertEquals(123L, space.updatedAt)
    }
}
