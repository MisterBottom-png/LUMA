package com.orbit.app.domain.search

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.ui.navigation.ItemDetailType
import java.text.Normalizer
import java.util.Locale

data class SearchCorpus(
    val captures: List<CaptureEntity>,
    val notes: List<NoteEntity>,
    val tasks: List<TaskEntity>,
    val reminders: List<ReminderEntity>,
    val spaces: List<SpaceEntity>,
)

data class LocalSearchResult(
    val type: ItemDetailType,
    val id: Long,
    val title: String,
    val snippet: String,
    val spaceName: String?,
    val status: LocalSearchStatus,
    val timestamp: Long,
) {
    val key: String = "${type.routeName}_$id"
}

enum class LocalSearchStatus {
    Note,
    Task,
    Reminder,
    CompletedReminder,
    Done,
    Archived,
    WaitingFor,
    Someday,
}

class LocalSearch {
    fun search(
        query: String,
        corpus: SearchCorpus,
        includeArchived: Boolean = false,
    ): List<LocalSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < MinQueryLength) return emptyList()
        val tokens = SearchText.tokens(cleanQuery)
        if (tokens.isEmpty()) return emptyList()
        val spacesById = corpus.spaces.associateBy { it.id }

        return buildList {
            corpus.notes
                .filter { includeArchived || !it.archived }
                .filter { SearchText.matchesAll(tokens, it.title, it.body) }
                .mapTo(this) { it.toResult(spacesById) }

            corpus.tasks
                .filter { includeArchived || it.status != TaskStatus.Archived }
                .filter { SearchText.matchesAll(tokens, it.title, it.notes) }
                .mapTo(this) { it.toResult(spacesById) }

            corpus.reminders
                .filter { SearchText.matchesAll(tokens, it.title, it.notes) }
                .mapTo(this) { it.toResult(spacesById) }
        }.sortedByDescending { it.timestamp }
    }

    /**
     * What Search shows before anything is typed: the newest notes, tasks and reminders,
     * newest first. Archived items stay out, and raw thoughts stay private here too.
     */
    fun recent(corpus: SearchCorpus, limit: Int = RecentLimit): List<LocalSearchResult> {
        val spacesById = corpus.spaces.associateBy { it.id }
        val newest = buildList {
            corpus.notes.filterNot { it.archived }.mapTo(this) { it.createdAt to it.toResult(spacesById) }
            corpus.tasks.filter { it.status != TaskStatus.Archived }.mapTo(this) { it.createdAt to it.toResult(spacesById) }
            corpus.reminders.mapTo(this) { it.createdAt to it.toResult(spacesById) }
        }
        return newest
            .sortedWith(compareByDescending<Pair<Long, LocalSearchResult>> { it.first }.thenByDescending { it.second.id })
            .take(limit)
            .map { it.second }
    }

    private fun NoteEntity.toResult(spacesById: Map<Long, SpaceEntity>) = LocalSearchResult(
        type = ItemDetailType.Note,
        id = id,
        title = title,
        snippet = body.ifBlank { title },
        spaceName = spaceId?.let(spacesById::get)?.name,
        status = if (archived) LocalSearchStatus.Archived else LocalSearchStatus.Note,
        timestamp = updatedAt,
    )

    private fun TaskEntity.toResult(spacesById: Map<Long, SpaceEntity>) = LocalSearchResult(
        type = ItemDetailType.Task,
        id = id,
        title = title,
        snippet = notes,
        spaceName = spaceId?.let(spacesById::get)?.name,
        status = status.label(),
        timestamp = updatedAt,
    )

    private fun ReminderEntity.toResult(spacesById: Map<Long, SpaceEntity>) = LocalSearchResult(
        type = ItemDetailType.Reminder,
        id = id,
        title = title,
        snippet = notes,
        spaceName = spaceId?.let(spacesById::get)?.name,
        status = if (completedAt == null) LocalSearchStatus.Reminder else LocalSearchStatus.CompletedReminder,
        timestamp = dueAt,
    )

    private companion object {
        // One letter is enough: results follow from the first key press.
        const val MinQueryLength = 1
        const val RecentLimit = 5
    }
}

/**
 * Matching that works the same in English, Estonian and Russian: Unicode
 * normalisation (é typed two ways is one letter), case folding, ё = е, and every
 * word of the query must appear somewhere in the item, in any order.
 */
internal object SearchText {
    private val Separators = Regex("[\\s\\p{P}\\p{S}]+")

    fun fold(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')

    fun tokens(query: String): List<String> =
        fold(query).split(Separators).filter { it.isNotEmpty() }

    fun matchesAll(tokens: List<String>, vararg fields: String): Boolean {
        val haystack = fields.joinToString(" ") { fold(it) }
        return tokens.all { haystack.contains(it) }
    }
}

private fun TaskStatus.label(): LocalSearchStatus = when (this) {
    TaskStatus.Open -> LocalSearchStatus.Task
    TaskStatus.Done -> LocalSearchStatus.Done
    TaskStatus.Archived -> LocalSearchStatus.Archived
    TaskStatus.WaitingFor -> LocalSearchStatus.WaitingFor
    TaskStatus.Someday -> LocalSearchStatus.Someday
}
