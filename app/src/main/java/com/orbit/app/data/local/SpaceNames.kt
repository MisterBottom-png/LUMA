package com.orbit.app.data.local

import com.orbit.app.data.local.entity.SpaceEntity
import java.text.Normalizer
import java.util.Locale

/**
 * One rule for Space names everywhere: "Work", " work " and "WORK" are the same
 * Space, and composed/decomposed Unicode letters (é vs e + ◌́) compare equal.
 */
object SpaceNames {
    private val Whitespace = Regex("\\s+")

    fun clean(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC).trim().replace(Whitespace, " ")

    fun normalize(value: String): String = clean(value).lowercase(Locale.ROOT)

    /** The Space (archived and hidden included) already using [candidate], if any. */
    fun conflict(candidate: String, spaces: List<SpaceEntity>, excludingSpaceId: Long? = null): SpaceEntity? {
        val normalized = normalize(candidate)
        if (normalized.isEmpty()) return null
        return spaces.firstOrNull { it.id != excludingSpaceId && normalize(it.name) == normalized }
    }
}

/** Thrown when a Space would share its name with another Space. */
class DuplicateSpaceNameException(val existing: SpaceEntity) :
    IllegalStateException("Another Space already uses this name")
