package com.orbit.app.integrations.gemini

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Real JSON reading for Gemini replies. Models sometimes wrap JSON in Markdown
 * fences or add a sentence around it, so the outermost object is located first and
 * then parsed with a proper parser; anything that does not parse is rejected.
 */
internal object GeminiJson {
    fun parseObject(text: String): JSONObject? {
        val trimmed = text.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(trimmed.substring(start, end + 1))
        } catch (_: JSONException) {
            null
        }
    }
}

/** Finds [key] on this object or, failing that, on nested objects (not arrays). */
internal fun JSONObject.deepValue(key: String): Any? {
    if (has(key)) return opt(key).takeUnless { it == JSONObject.NULL }
    keys().forEach { name ->
        val nested = optJSONObject(name) ?: return@forEach
        nested.deepValue(key)?.let { return it }
    }
    return null
}

internal fun JSONObject.stringValue(key: String): String? =
    (deepValue(key) as? String)?.trim()

internal fun JSONObject.numberValue(key: String): Double? = when (val value = deepValue(key)) {
    is Number -> value.toDouble()
    else -> null
}

internal fun JSONObject.booleanValue(key: String): Boolean? = deepValue(key) as? Boolean

internal fun JSONObject.stringList(key: String, limit: Int): List<String> {
    val array = deepValue(key) as? JSONArray ?: return emptyList()
    return (0 until array.length())
        .mapNotNull { array.opt(it) as? String }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(limit)
}
