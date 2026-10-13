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

    /**
     * A new Space needs only a name: its colour and icon come from the name, the same
     * every time, so "Garden" always looks like "Garden" until the user picks otherwise.
     * Colour and icon use different seeds, so they do not move in step.
     */
    fun autoColorIndex(name: String, count: Int): Int = autoIndex(name, count, seed = ColorSeed)

    /**
     * Review: an icon only when the name says what it is ("Car", "Kodu", "Деньги");
     * otherwise a plain folder, never an unrelated picture ("Garden" was a graduation cap).
     */
    fun autoIcon(name: String): String {
        val words = normalize(name).split(' ', '-', '/', '&').filter { it.isNotEmpty() }
        return IconWords.firstOrNull { (_, stems) -> words.any { word -> stems.any(word::startsWith) } }?.first ?: "folder"
    }

    private val IconWords: List<Pair<String, List<String>>> = listOf(
        "work" to listOf("work", "job", "office", "töö", "tööl", "работ", "офис"),
        "home" to listOf("home", "house", "kodu", "maja", "дом", "квартир"),
        "directions_car" to listOf("car", "auto", "машин", "авто"),
        "pets" to listOf("pet", "dog", "cat", "koer", "kass", "lemmik", "собак", "кошк", "кот", "питом"),
        "payments" to listOf("money", "financ", "bill", "budget", "raha", "arve", "eelarve", "деньг", "финанс", "счет", "счёт", "бюджет"),
        "lightbulb" to listOf("idea", "idee", "mõte", "mõtted", "иде"),
        "favorite" to listOf("health", "family", "love", "tervis", "pere", "здоров", "семь", "любов"),
        "school" to listOf("school", "study", "learn", "course", "kool", "õpe", "õpi", "kursus", "школ", "учёб", "учеб", "курс"),
        "palette" to listOf("art", "design", "hobby", "craft", "kunst", "hobi", "искусств", "дизайн", "хобби"),
        "flight" to listOf("travel", "trip", "holiday", "vacation", "reis", "puhkus", "путешеств", "поездк", "отпуск"),
        "person" to listOf("personal", "me", "self", "isiklik", "mina", "личн"),
    )

    private fun autoIndex(name: String, count: Int, seed: Int): Int {
        require(count > 0) { "count must be positive" }
        val key = normalize(name)
        if (key.isEmpty()) return 0
        val hash = key.fold(seed) { acc, char -> acc * 31 + char.code }
        return Math.floorMod(hash, count)
    }

    private const val ColorSeed = 17
}

/** Thrown when a Space would share its name with another Space. */
class DuplicateSpaceNameException(val existing: SpaceEntity) :
    IllegalStateException("Another Space already uses this name")
