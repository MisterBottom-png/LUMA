package com.orbit.app.domain.analyzer

/** One thought cut out of a dump: [text] is what is kept, [title] what the row shows. */
data class BrainDumpFragment(val text: String, val title: String)

/**
 * Splits a dump into thoughts using only safe, local signs. It never splits on "and"
 * or commas ("salt and pepper"), never invents words, and keeps text in order.
 */
object BrainDumpSplitter {
    private val Url = Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE)
    private val ListMarker = Regex("""^(?:[-*•–—]|\d{1,3}[.)]|\[[ xX]?])\s+""")
    private val InlineNumber = Regex("""(?:^|\s)\(?(\d{1,2})[.)]\s+""")
    private val SentenceEnd = Regex("""(?<=[.!?])\s+(?=\p{Lu})""")
    private val ActionChunkSeparators = Regex("""\s*(?:[,;]|\band\b|\bthen\b|\bja\b|\bning\b|\bи\b|\bпотом\b)\s*""", RegexOption.IGNORE_CASE)

    /** Removes a leading bullet, number or checkbox: "1. call bank" → "call bank". */
    fun cleanLine(line: String): String {
        var text = line.trim()
        while (true) {
            val next = ListMarker.replaceFirst(text, "").trim()
            if (next == text) break
            text = next
        }
        return text
    }

    private fun isListLine(line: String): Boolean = ListMarker.containsMatchIn(line.trim())

    private fun isUrl(line: String): Boolean = Url.matches(line.trim())

    /**
     * Thoughts on separate lines. A link alone on a line stays with the line above it;
     * a heading that ends with ":" keeps the list under it. Fewer than two thoughts
     * means it is not a dump.
     */
    fun lineFragments(rawText: String): List<BrainDumpFragment> {
        val lines = rawText.lines().map(String::trim).filter(String::isNotBlank)
        val fragments = mutableListOf<BrainDumpFragment>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val cleaned = cleanLine(line)
            when {
                isUrl(cleaned) && fragments.isNotEmpty() -> {
                    val previous = fragments.removeAt(fragments.lastIndex)
                    fragments += previous.copy(text = previous.text + "\n" + cleaned)
                    index++
                }
                cleaned.endsWith(":") && index + 1 < lines.size && isListLine(lines[index + 1]) -> {
                    val heading = cleaned.removeSuffix(":").trim()
                    val items = mutableListOf<String>()
                    var next = index + 1
                    while (next < lines.size && isListLine(lines[next])) {
                        items += cleanLine(lines[next])
                        next++
                    }
                    fragments += BrainDumpFragment(
                        text = (listOf(cleaned) + items.map { "- $it" }).joinToString("\n"),
                        title = heading.ifBlank { items.first() },
                    )
                    index = next
                }
                cleaned.isNotBlank() -> {
                    fragments += BrainDumpFragment(text = cleaned, title = cleaned)
                    index++
                }
                else -> index++
            }
        }
        return fragments.takeIf { it.size >= 2 }.orEmpty()
    }

    /** The list lines under a "heading:" fragment, so the user can split it after all. */
    fun listItemsOf(fragmentText: String): List<String> =
        fragmentText.lines().drop(1).map(::cleanLine).filter(String::isNotBlank)

    /**
     * Parts of a one-line thought, split only on safe signs: ";", inline numbers
     * ("1) … 2) …"), or sentence ends where every sentence starts with an action word.
     */
    fun oneLineParts(rawText: String, startsWithAction: (String) -> Boolean): List<String> {
        val text = rawText.trim()
        if (text.isEmpty() || text.lines().count(String::isNotBlank) > 1) return emptyList()

        val bySemicolon = text.split(';').map(String::trim).filter(String::isNotBlank)
        if (bySemicolon.size >= 2) return bySemicolon

        val numbered = InlineNumber.findAll(text).toList()
        if (numbered.size >= 2 && numbered.map { it.groupValues[1].toInt() } == (1..numbered.size).toList()) {
            val starts = numbered.map { it.range.first }
            val parts = starts.mapIndexed { i, start ->
                text.substring(start, if (i + 1 < starts.size) starts[i + 1] else text.length)
            }.map { cleanLine(it).trim().trimEnd(',', ';') }.filter(String::isNotBlank)
            if (parts.size >= 2) return parts
        }

        val sentences = text.split(SentenceEnd).map(String::trim).filter(String::isNotBlank)
        if (sentences.size >= 2 && sentences.all { startsWithAction(it.trimEnd('.', '!', '?')) }) {
            return sentences.map { it.trimEnd('.') }
        }
        return emptyList()
    }

    /** Comma / "and" chunks, used only to notice that a line may hold several actions. */
    fun actionChunks(rawText: String): List<String> =
        rawText.trim().split(ActionChunkSeparators).map(String::trim).filter(String::isNotBlank)

    /**
     * Gemini's parts are accepted only when each one is copied word for word from the
     * text, in order, without overlap, and what lies between them is only joining
     * words and punctuation.
     */
    fun partsAreExactCopies(rawText: String, parts: List<String>): Boolean {
        if (parts.size < 2) return false
        var cursor = 0
        val gaps = mutableListOf<String>()
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return false
            val found = rawText.indexOf(trimmed, startIndex = cursor)
            if (found < 0) return false
            gaps += rawText.substring(cursor, found)
            cursor = found + trimmed.length
        }
        gaps += rawText.substring(cursor)
        return gaps.all { gap -> gap.replace(ActionChunkSeparators, " ").trim(' ', '.', '!', '?', ':', '-', '–', '—', '\n').isEmpty() }
    }
}
