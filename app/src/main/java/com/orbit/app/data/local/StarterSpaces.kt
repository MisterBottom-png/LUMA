package com.orbit.app.data.local

import com.orbit.app.data.local.entity.SpaceEntity

data class StarterSpaceTemplate(
    val key: String,
    val storedName: String,
    val icon: String,
    val colorAccent: String,
)

object StarterSpaces {
    val templates: List<StarterSpaceTemplate> = listOf(
        StarterSpaceTemplate("personal", "Personal", "person", "#B270D6"),
        StarterSpaceTemplate("work", "Work", "work", "#6D7CFF"),
        StarterSpaceTemplate("home", "Home", "home", "#D7798D"),
        StarterSpaceTemplate("health", "Health", "favorite", "#59A6A6"),
        StarterSpaceTemplate("money", "Money", "payments", "#62A77A"),
        StarterSpaceTemplate("learning", "Learning", "school", "#7B8CB8"),
    )

    fun spaceFor(
        template: StarterSpaceTemplate,
        name: String = template.storedName,
        sortOrder: Int,
        now: Long = System.currentTimeMillis(),
    ): SpaceEntity = SpaceEntity(
        name = name,
        icon = template.icon,
        colorAccent = template.colorAccent,
        sortOrder = sortOrder,
        createdAt = now,
        updatedAt = now,
    )
}
