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
                .mapTo(this) {
                    LocalSearchResult(
                        type = ItemDetailType.Note,
                        id = it.id,
                        title = it.title,
                        snippet = it.body.ifBlank { it.title },
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = if (it.archived) LocalSearchStatus.Archived else LocalSearchStatus.Note,
                        timestamp = it.updatedAt,
                    )
                }

            corpus.tasks
                .filter { includeArchived || it.status != TaskStatus.Archived }
                .filter { SearchText.matchesAll(tokens, it.title, it.notes) }
                .mapTo(this) {
                    LocalSearchResult(
                        type = ItemDetailType.Task,
                        id = it.id,
                        title = it.title,
                        snippet = it.notes,
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = it.status.label(),
                        timestamp = it.updatedAt,
                    )
                }

            corpus.reminders
                .filter { SearchText.matchesAll(tokens, it.title, it.notes) }
                .mapTo(this) {
                    LocalSearchResult(
                        type = ItemDetailType.Reminder,
                        id = it.id,
                        title = it.title,
                        snippet = it.notes,
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = if (it.completedAt == null) {
                            LocalSearchStatus.Reminder
                        } else {
                            LocalSearchStatus.CompletedReminder
                        },
                        timestamp = it.dueAt,
                    )
                }
        }.sortedByDescending { it.timestamp }
    }

    private companion object {
        const val MinQueryLength = 2
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
