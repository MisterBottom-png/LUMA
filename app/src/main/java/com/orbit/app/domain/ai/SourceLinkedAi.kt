package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.search.SearchCorpus
import com.orbit.app.ui.navigation.ItemDetailType
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class AiSourceItem(
    val sourceId: String,
    val type: ItemDetailType,
    val itemId: Long,
    val title: String,
    val snippet: String,
    val spaceName: String?,
    val status: String,
    val timestamp: Long,
    val createdAt: Long = timestamp,
    val dueAt: Long? = null,
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
        now: Long = System.currentTimeMillis(),
    ): List<AiSourceItem> {
        val tokens = query.queryTokens()
        val intent = QueryIntent.from(query, tokens)
        val spacesById = corpus.spaces.associateBy { it.id }
        return buildList {
            corpus.captures
                .filter { it.status == CaptureStatus.Inbox }
                .mapTo(this) {
                    AiSourceItem(
                        sourceId = ItemDetailType.Capture.sourceId(it.id),
                        type = ItemDetailType.Capture,
                        itemId = it.id,
                        title = it.rawText.firstLineOr(),
                        snippet = it.rawText,
                        spaceName = it.suggestedSpaceId?.let(spacesById::get)?.name,
                        status = it.status.name,
                        timestamp = it.updatedAt,
                        createdAt = it.createdAt,
                    )
                }
            corpus.notes
                .filterNot { it.archived }
                .mapTo(this) {
                    AiSourceItem(
                        sourceId = ItemDetailType.Note.sourceId(it.id),
                        type = ItemDetailType.Note,
                        itemId = it.id,
                        title = it.title,
                        snippet = it.body.ifBlank { it.title },
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = "Note",
                        timestamp = it.updatedAt,
                        createdAt = it.createdAt,
                    )
                }
            corpus.tasks
                .filter { task ->
                    if (intent.completed) task.status == TaskStatus.Done else task.status != TaskStatus.Done && task.status != TaskStatus.Archived
                }
                .mapTo(this) {
                    AiSourceItem(
                        sourceId = ItemDetailType.Task.sourceId(it.id),
                        type = ItemDetailType.Task,
                        itemId = it.id,
                        title = it.title,
                        snippet = it.notes.ifBlank { it.status.name },
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = it.status.name,
                        timestamp = it.updatedAt,
                        createdAt = it.createdAt,
                        dueAt = it.dueAt,
                    )
                }
            corpus.reminders
                .filter { if (intent.completed) it.completedAt != null else it.completedAt == null }
                .mapTo(this) {
                    AiSourceItem(
                        sourceId = ItemDetailType.Reminder.sourceId(it.id),
                        type = ItemDetailType.Reminder,
                        itemId = it.id,
                        title = it.title,
                        snippet = it.notes,
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = if (it.completedAt == null) "Reminder" else "Completed",
                        timestamp = it.updatedAt,
                        createdAt = it.createdAt,
                        dueAt = it.dueAt,
                    )
                }
        }
            .filter { item -> intent.accepts(item, now) }
            .map { item -> item to item.score(tokens, query, intent, now) }
            .filter { (_, score) -> tokens.isEmpty() || intent.common || score > 0 }
            .sortedWith(
                compareByDescending<Pair<AiSourceItem, Int>> { it.second }
                    .thenBy { if (intent.due) it.first.dueAt ?: Long.MAX_VALUE else Long.MIN_VALUE }
                    .thenByDescending { it.first.timestamp },
            )
            .map { it.first.compact() }
            .take(limit)
    }

    fun recentContext(corpus: SearchCorpus, limit: Int = DefaultLimit): List<AiSourceItem> =
        retrieve("", corpus, limit)

    private fun AiSourceItem.score(tokens: List<String>, query: String, intent: QueryIntent, now: Long): Int {
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
        val typeScore = if (intent.types.isEmpty() || type in intent.types) 4 else 0
        val urgencyScore = when {
            dueAt != null && dueAt < now && intent.overdue -> 8
            dueAt != null && dueAt >= now && intent.due -> 6
            status.equals("WaitingFor", ignoreCase = true) && intent.stuck -> 8
            intent.attention && dueAt != null && dueAt < now -> 8
            intent.attention && dueAt != null && dueAt <= now + TimeUnit.DAYS.toMillis(DueSoonDays) -> 6
            intent.attention && status.equals("WaitingFor", ignoreCase = true) -> 7
            intent.attention && timestamp >= now - TimeUnit.DAYS.toMillis(RecentDays) -> 2
            intent.recent -> 3
            else -> 0
        }
        return exact + tokenScore + statusScore + typeScore + urgencyScore
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

    private fun String.firstLineOr(): String =
        lineSequence().firstOrNull()?.trim().orEmpty()

    private fun String.compactText(maxLength: Int): String =
        replace(Regex("\\s+"), " ").trim().take(maxLength)

    private fun ItemDetailType.sourceId(itemId: Long): String = "$routeName:$itemId"

    private data class QueryIntent(
        val completed: Boolean,
        val overdue: Boolean,
        val due: Boolean,
        val today: Boolean,
        val soon: Boolean,
        val stuck: Boolean,
        val recent: Boolean,
        val attention: Boolean,
        val types: Set<ItemDetailType>,
        val common: Boolean,
    ) {
        fun accepts(item: AiSourceItem, now: Long): Boolean = when {
            types.isNotEmpty() && item.type !in types -> false
            completed -> item.status.equals(TaskStatus.Done.name, ignoreCase = true) || item.status.equals("Completed", ignoreCase = true)
            overdue -> item.dueAt?.let { it < now } == true
            today -> item.dueAt?.let { dueAt ->
                dueAt >= now && Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).toLocalDate() ==
                    Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            } == true
            soon -> item.dueAt?.let { it in now..(now + TimeUnit.DAYS.toMillis(DueSoonDays)) } == true
            due -> item.dueAt?.let { it >= now } == true
            stuck -> item.status.equals("WaitingFor", ignoreCase = true)
            recent -> item.timestamp >= now - TimeUnit.DAYS.toMillis(RecentDays)
            else -> true
        }

        companion object {
            fun from(query: String, tokens: List<String>): QueryIntent {
                val lower = query.lowercase()
                val overdue = "overdue" in tokens || "late" in tokens
                val today = !overdue && "today" in tokens
                val soon = !overdue && "soon" in tokens
                val due = !overdue && ("due" in tokens || "upcoming" in tokens || today || soon)
                val stuck = tokens.any { it in WaitingTokens }
                val recent = tokens.any { it in setOf("recent", "recently", "latest", "new", "captured") }
                val completed = tokens.any { it in setOf("completed", "complete", "done", "finish", "finished") }
                val attention = listOf("attention", "matter", "matters", "next").any(lower::contains)
                val types = buildSet {
                    if (tokens.any { it in setOf("capture", "captures", "inbox") }) add(ItemDetailType.Capture)
                    if (tokens.any { it in setOf("note", "notes") }) add(ItemDetailType.Note)
                    if (tokens.any { it in setOf("task", "tasks") }) add(ItemDetailType.Task)
                    if (tokens.any { it in setOf("reminder", "reminders") }) add(ItemDetailType.Reminder)
                }
                val common = overdue || due || stuck || recent || completed || attention || types.isNotEmpty() ||
                    "local items" in lower
                return QueryIntent(completed, overdue, due, today, soon, stuck, recent, attention, types, common)
            }
        }
    }

    private companion object {
        const val DefaultLimit = 12
        const val MaxTitleLength = 90
        const val MaxSnippetLength = 180
        const val DueSoonDays = 7L
        const val RecentDays = 7L
        val WaitingTokens = setOf("stuck", "blocked", "waiting")
        val StopWords = setOf("what", "about", "with", "from", "that", "this", "did", "the", "and")
    }
}
