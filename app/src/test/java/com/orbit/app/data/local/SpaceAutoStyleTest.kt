package com.orbit.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Space made from its name alone gets a steady colour and icon from that name. */
class SpaceAutoStyleTest {
    private val names = listOf(
        "Garden", "Work", "Kids", "Car", "Money", "Health", "Travel", "Books",
        "Aed", "Töö", "Lapsed", "Raha", "Сад", "Работа", "Дети", "Деньги",
    )

    @Test
    fun picksStayInRange_andTheSameNameAlwaysLooksTheSame() {
        names.forEach { name ->
            val color = SpaceNames.autoColorIndex(name, 8)
            assertTrue(color in 0 until 8)
            assertEquals(color, SpaceNames.autoColorIndex("  ${name.uppercase()} ", 8))
            assertEquals(SpaceNames.autoIcon(name), SpaceNames.autoIcon("  ${name.uppercase()} "))
        }
    }

    @Test
    fun differentNamesSpreadOverTheChoices() {
        assertTrue(names.map { SpaceNames.autoColorIndex(it, 8) }.toSet().size >= 4)
        assertTrue(names.map { SpaceNames.autoIcon(it) }.toSet().size >= 5)
    }

    @Test
    fun aBlankNameUsesTheFirstChoice() {
        assertEquals(0, SpaceNames.autoColorIndex("", 8))
        assertEquals("folder", SpaceNames.autoIcon("   "))
    }

    @Test
    fun theIconMatchesTheName_orIsAPlainFolder() {
        // Review: "Garden" no longer gets a graduation cap; known words get their picture.
        assertEquals("folder", SpaceNames.autoIcon("Garden"))
        assertEquals("work", SpaceNames.autoIcon("Töö"))
        assertEquals("directions_car", SpaceNames.autoIcon("Car"))
        assertEquals("payments", SpaceNames.autoIcon("Деньги"))
        assertEquals("flight", SpaceNames.autoIcon("Summer travel"))
    }
}
