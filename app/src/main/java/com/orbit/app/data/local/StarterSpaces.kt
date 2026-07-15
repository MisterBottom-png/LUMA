package com.orbit.app.data.local

import com.orbit.app.data.local.entity.SpaceEntity

object StarterSpaces {
    val names: List<String> = listOf(
        "Work",
        "Personal",
        "Car",
        "Dog",
        "Money",
        "Ideas",
        "Home",
        "Health",
        "Learning",
    )

    fun entities(createdAt: Long = System.currentTimeMillis()): List<SpaceEntity> =
        names.mapIndexed { index, name ->
            SpaceEntity(
                id = (index + 1).toLong(),
                name = name,
                icon = iconFor(name),
                colorAccent = accentFor(index),
                sortOrder = index,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }

    private fun iconFor(name: String): String = when (name) {
        "Work" -> "work"
        "Personal" -> "person"
        "Car" -> "directions_car"
        "Dog" -> "pets"
        "Money" -> "payments"
        "Ideas" -> "lightbulb"
        "Home" -> "home"
        "Health" -> "favorite"
        else -> "school"
    }

    private fun accentFor(index: Int): String = listOf(
        "#6D7CFF",
        "#B270D6",
        "#4E91D8",
        "#D58B62",
        "#62A77A",
        "#B38BDB",
        "#D7798D",
        "#59A6A6",
        "#7B8CB8",
    )[index]
}
