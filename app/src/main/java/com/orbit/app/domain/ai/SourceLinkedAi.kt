package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.search.SearchCorpus
import com.orbit.app.ui.navigation.ItemDetailType

data class AiSourceItem(
    val sourceId: String,
    val type: ItemDetailType,
    val itemId: Long,
    val title: String,
    val snippet: String,
    val spaceName: String?,
    val status: String,
    val timestamp: Long,
)

data class SourceLinkedAnswer(
    val answer: String,
    val sourceItemIds: List<String>,
    val sourceItems: List<AiSourceItem>,
    val fromGemini: Boolean,
)

data class SituationSourceSummary(
    val rightNow: String,
    val whatMatters: String,
    val stuck: String,
    val nextTinyStep: String,
    val sourceItemIds: List<String>,
    val sourceItems: List<AiSourceItem>,
    val fromGemini: Boolean,
)

class LocalAiRetriever {
    fun retrieve(
        query: String,
        corpus: SearchCorpus,
        limit: Int = DefaultLimit,
    ): List<AiSourceItem> {
        val tokens = query.queryTokens()
        val spacesById = corpus.spaces.associateBy { it.id }
        return buildList {
            corpus.captures
                .filter { it.status != CaptureStatus.Archived }
                .mapTo(this) {
                    AiSourceItem(
                        sourceId = ItemDetailType.Capture.sourceId(it.id),
                        type = ItemDetailType.Capture,
                        itemId = it.id,
                        title = it.rawText.firstLineOr("Capture"),
                        snippet = it.rawText,
                        spaceName = it.suggestedSpaceId?.let(spacesById::get)?.name,
                        status = it.status.name,
                        timestamp = it.updatedAt,
                    )
                }
            corpus.notes
                .filterNot { it.archived }
                .mapTo(this) {
                    AiSourceItem(
                        sourceId = ItemDetailType.Note.sourceId(it.id),
                        type = ItemDetailType.Note,
                        itemId = it.id,
                        title = it.title.ifBlank { "Untitled note" },
                        snippet = it.body.ifBlank { it.title },
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = "Note",
                        timestamp = it.updatedAt,
                    )
                }
            corpus.tasks
                .filter { it.status != TaskStatus.Archived }
                .mapTo(this) {
                    AiSourceItem(
                        sourceId = ItemDetailType.Task.sourceId(it.id),
                        type = ItemDetailType.Task,
                        itemId = it.id,
                        title = it.title.ifBlank { "Untitled task" },
                        snippet = it.notes.ifBlank { it.status.name },
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = it.status.name,
                        timestamp = it.updatedAt,
                    )
                }
            corpus.reminders
                .filter { it.completedAt == null }
                .mapTo(this) {
                    AiSourceItem(
                        sourceId = ItemDetailType.Reminder.sourceId(it.id),
                        type = ItemDetailType.Reminder,
                        itemId = it.id,
                        title = it.title.ifBlank { "Untitled reminder" },
                        snippet = it.notes.ifBlank { "Reminder" },
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = "Reminder",
                        timestamp = it.updatedAt,
                    )
                }
        }
            .map { item -> item to item.score(tokens, query) }
            .filter { (_, score) -> tokens.isEmpty() || score > 0 }
            .sortedWith(
                compareByDescending<Pair<AiSourceItem, Int>> { it.second }
                    .thenByDescending { it.first.timestamp },
            )
            .map { it.first.compact() }
            .take(limit)
    }

    fun recentContext(corpus: SearchCorpus, limit: Int = DefaultLimit): List<AiSourceItem> =
        retrieve("", corpus, limit)

    private fun AiSourceItem.score(tokens: List<String>, query: String): Int {
        if (tokens.isEmpty()) return 1
        val haystack = listOf(title, snippet, spaceName.orEmpty(), status)
            .joinToString(" ")
            .lowercase()
        val exact = if (query.isNotBlank() && haystack.contains(query.lowercase())) 6 else 0
        val tokenScore = tokens.count { haystack.contains(it) } * 3
        val statusScore = when {
            status.equals("WaitingFor", ignoreCase = true) && tokens.any { it in WaitingTokens } -> 4
            status.equals("Someday", ignoreCase = true) && tokens.any { it == "someday" } -> 4
            else -> 0
        }
        return exact + tokenScore + statusScore
    }

    private fun AiSourceItem.compact(): AiSourceItem =
        copy(
            title = title.compactText(MaxTitleLength),
            snippet = snippet.compactText(MaxSnippetLength),
        )

    private fun String.queryTokens(): List<String> =
        lowercase()
            .split(Regex("[^a-z0-9A-Z]+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in StopWords }
            .distinct()

    private fun String.firstLineOr(fallback: String): String =
        lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: fallback

    private fun String.compactText(maxLength: Int): String =
        replace(Regex("\\s+"), " ").trim().take(maxLength)

    private fun ItemDetailType.sourceId(itemId: Long): String = "$routeName:$itemId"

    private companion object {
        const val DefaultLimit = 12
        const val MaxTitleLength = 90
        const val MaxSnippetLength = 180
        val WaitingTokens = setOf("stuck", "blocked", "waiting", "work")
        val StopWords = setOf("what", "about", "with", "from", "that", "this", "did", "the", "and")
    }
}
