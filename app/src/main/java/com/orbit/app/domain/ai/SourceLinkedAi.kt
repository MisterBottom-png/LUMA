package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.search.SearchCorpus
import com.orbit.app.domain.search.SearchText
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
    /** Set when nothing matched: the words to offer in Search instead. */
    val searchQuery: String? = null,
)

/** Words for a local answer, in [locale]; [titles] are the cited items' titles. */
fun interface LocalAnswerText {
    fun text(locale: java.util.Locale, kind: LocalAnswerKind, titles: String): String
}

data class SituationSourceSummary(
    val rightNow: String,
    val whatMatters: String,
    val stuck: String,
    val nextTinyStep: String,
    val sourceItemIds: List<String>,
    val sourceItems: List<AiSourceItem>,
    val fromGemini: Boolean,
)

/** How a local answer is worded; the words themselves are string resources. */
enum class LocalAnswerKind { NoMatch, NoData, Matches, Overdue, Waiting, Completed, Upcoming, Recent }

class LocalAiRetriever {
    fun retrieve(
        query: String,
        corpus: SearchCorpus,
        limit: Int = DefaultLimit,
        now: Long = System.currentTimeMillis(),
    ): List<AiSourceItem> {
        val tokens = SearchText.tokens(query)
        val intent = QueryIntent.from(tokens)
        val content = tokens.filter { token ->
            token.length >= 3 && token !in StopWords && QuestionWords.none { it.matches(token) }
        }.distinct()
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
            .map { item -> item to item.wordMatches(content) }
            .let { candidates ->
                val matching = candidates.filter { (_, matches) -> matches > 0 }
                when {
                    // An ordinary question cites only items that share a word with it.
                    !intent.isStateQuestion -> matching
                    // A state question (overdue, waiting, ...) needs no matching word, but
                    // when some items do share one, those are the ones meant.
                    matching.isNotEmpty() -> matching
                    else -> candidates
                }
            }
            .map { (item, matches) -> item to item.score(matches, query, intent, now) }
            .sortedWith(
                compareByDescending<Pair<AiSourceItem, Int>> { it.second }
                    .thenBy { if (intent.due) it.first.dueAt ?: Long.MAX_VALUE else Long.MIN_VALUE }
                    .thenByDescending { it.first.timestamp },
            )
            .map { it.first.compact() }
            .take(limit)
    }

    /** Open items, most recently changed first. */
    fun recentContext(corpus: SearchCorpus, limit: Int = DefaultLimit): List<AiSourceItem> =
        retrieve(RecentQuery, corpus, limit)

    /** How a local answer to [question] should be worded. */
    fun answerKind(question: String): LocalAnswerKind {
        val intent = QueryIntent.from(SearchText.tokens(question))
        return when {
            intent.overdue -> LocalAnswerKind.Overdue
            intent.stuck -> LocalAnswerKind.Waiting
            intent.completed -> LocalAnswerKind.Completed
            intent.due -> LocalAnswerKind.Upcoming
            intent.recent -> LocalAnswerKind.Recent
            else -> LocalAnswerKind.Matches
        }
    }

    private fun AiSourceItem.wordMatches(content: List<String>): Int {
        if (content.isEmpty()) return 0
        val haystack = SearchText.fold(listOf(title, snippet, spaceName.orEmpty()).joinToString(" "))
        return content.count { word -> haystack.contains(word.stem()) }
    }

    private fun AiSourceItem.score(matches: Int, query: String, intent: QueryIntent, now: Long): Int {
        val haystack = SearchText.fold(listOf(title, snippet, spaceName.orEmpty()).joinToString(" "))
        val exact = if (query.isNotBlank() && haystack.contains(SearchText.fold(query.trim()))) 6 else 0
        val urgencyScore = when {
            dueAt != null && dueAt < now && intent.overdue -> 8
            dueAt != null && dueAt >= now && intent.due -> 6
            status.equals("WaitingFor", ignoreCase = true) && intent.stuck -> 8
            intent.attention && dueAt != null && dueAt < now -> 8
            intent.attention && dueAt != null && dueAt <= now + TimeUnit.DAYS.toMillis(DueSoonDays) -> 6
            intent.attention && status.equals("WaitingFor", ignoreCase = true) -> 7
            intent.recent -> 3
            else -> 0
        }
        return exact + matches * 3 + urgencyScore
    }

    private fun AiSourceItem.compact(): AiSourceItem =
        copy(
            title = title.compactText(MaxTitleLength),
            snippet = snippet.compactText(MaxSnippetLength),
        )

    /** A light stem so "milk" finds "milking" and "молоко" finds "молока". */
    private fun String.stem(): String = when {
        length >= 8 -> dropLast(3)
        length >= 6 -> dropLast(2)
        length >= 5 -> dropLast(1)
        else -> this
    }

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
    ) {
        /** A question about the state of things, answerable without a matching word. */
        val isStateQuestion: Boolean
            get() = overdue || due || stuck || recent || completed || attention || types.isNotEmpty()

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
            attention -> item.dueAt?.let { it <= now + TimeUnit.DAYS.toMillis(DueSoonDays) } == true ||
                item.status.equals("WaitingFor", ignoreCase = true)
            else -> true
        }

        companion object {
            fun from(tokens: List<String>): QueryIntent {
                fun any(words: QuestionWord) = tokens.any(words::matches)
                val overdue = any(Overdue)
                val today = !overdue && any(Today)
                val soon = !overdue && any(Soon)
                val due = !overdue && (today || soon)
                val types = buildSet {
                    if (any(CaptureWords)) add(ItemDetailType.Capture)
                    if (any(NoteWords)) add(ItemDetailType.Note)
                    if (any(TaskWords)) add(ItemDetailType.Task)
                    if (any(ReminderWords)) add(ItemDetailType.Reminder)
                }
                return QueryIntent(
                    completed = any(Completed),
                    overdue = overdue,
                    due = due,
                    today = today,
                    soon = soon,
                    stuck = any(Waiting),
                    recent = any(Recent),
                    attention = any(Attention),
                    types = types,
                )
            }
        }
    }

    /**
     * Question words in English, Estonian and Russian. Plain entries must equal a
     * token; entries ending in '*' match tokens that start with them.
     */
    private class QuestionWord(vararg entries: String) {
        private val exact = entries.filterNot { it.endsWith('*') }.toSet()
        private val prefixes = entries.filter { it.endsWith('*') }.map { it.dropLast(1) }

        fun matches(token: String): Boolean = token in exact || prefixes.any(token::startsWith)
    }

    private companion object {
        const val DefaultLimit = 12
        const val MaxTitleLength = 90
        const val MaxSnippetLength = 180
        const val DueSoonDays = 7L
        const val RecentDays = 7L
        const val RecentQuery = "recent"

        val Overdue = QuestionWord("overdue", "late", "hilin*", "tähtaja*", "просроч*", "опозд*")
        val Today = QuestionWord("today", "täna", "сегодня*")
        val Soon = QuestionWord("due", "upcoming", "soon", "varsti", "tulemas", "tulekul", "скоро", "предстоящ*", "ближайш*")
        val Waiting = QuestionWord("stuck", "blocked", "waiting", "oota*", "ootel", "kinni", "жд*", "ожида*", "застр*", "заблокир*")
        val Completed = QuestionWord(
            "completed", "complete", "done", "finish", "finished",
            "tehtud", "valmis", "lõpeta*", "выполн*", "сделан*", "заверш*", "готов*",
        )
        val Recent = QuestionWord("recent", "recently", "latest", "new", "captured", "hiljuti", "viimati", "viimased", "uued", "uus", "недавн*", "последн*", "новы*", "новое")
        val Attention = QuestionWord("attention", "matter", "matters", "next", "tähelepanu*", "oluli*", "järgmi*", "вниман*", "важн*", "следующ*")
        val CaptureWords = QuestionWord("capture", "captures", "inbox", "thought", "thoughts", "mõte", "mõtted", "mõtteid", "mõtet", "мысл*", "входящ*")
        val NoteWords = QuestionWord("note", "notes", "märge", "märkme*", "märkus*", "заметк*", "заметок")
        val TaskWords = QuestionWord("task", "tasks", "ülesan*", "задач*")
        val ReminderWords = QuestionWord("reminder", "reminders", "meeldetulet*", "напомин*")
        val QuestionWords = listOf(Overdue, Today, Soon, Waiting, Completed, Recent, Attention, CaptureWords, NoteWords, TaskWords, ReminderWords)

        val StopWords = setOf(
            // English
            "what", "about", "with", "from", "that", "this", "did", "the", "and", "are", "any", "anything",
            "have", "has", "was", "were", "which", "who", "how", "when", "where", "for", "you", "your",
            "mine", "there", "show", "tell", "list", "all", "does", "can",
            // Estonian
            "mis", "mida", "kas", "kus", "kes", "kui", "mul", "minu", "mulle", "olen", "oli", "olid", "ning",
            "selle", "see", "need", "kõik", "näita", "millal", "kuidas", "miks", "üle", "veel", "midagi",
            // Russian
            "что", "как", "где", "кто", "когда", "мне", "мой", "мои", "моя", "мое", "моё", "это", "эти",
            "был", "была", "были", "есть", "все", "всё", "или", "про", "для", "чем", "какие", "какой",
            "покажи", "нужно", "надо", "уже", "ещё", "еще", "что-то",
        )
    }
}
