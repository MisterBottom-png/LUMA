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
            val icon = SpaceNames.autoIconIndex(name, 12)
            assertTrue(color in 0 until 8)
            assertTrue(icon in 0 until 12)
            assertEquals(color, SpaceNames.autoColorIndex("  ${name.uppercase()} ", 8))
            assertEquals(icon, SpaceNames.autoIconIndex(name.lowercase(), 12))
        }
    }

    @Test
    fun differentNamesSpreadOverTheChoices() {
        assertTrue(names.map { SpaceNames.autoColorIndex(it, 8) }.toSet().size >= 4)
        assertTrue(names.map { SpaceNames.autoIconIndex(it, 12) }.toSet().size >= 5)
    }

    @Test
    fun aBlankNameUsesTheFirstChoice() {
        assertEquals(0, SpaceNames.autoColorIndex("", 8))
        assertEquals(0, SpaceNames.autoIconIndex("   ", 12))
    }
}
