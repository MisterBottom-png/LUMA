package com.orbit.app.domain.search

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.ui.navigation.ItemDetailType

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
    val status: String,
    val timestamp: Long,
) {
    val key: String = "${type.routeName}_$id"
}

class LocalSearch {
    fun search(
        query: String,
        corpus: SearchCorpus,
        includeArchived: Boolean = false,
    ): List<LocalSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < MinQueryLength) return emptyList()
        val spacesById = corpus.spaces.associateBy { it.id }

        return buildList {
            corpus.notes
                .filter { includeArchived || !it.archived }
                .filter { it.title.matchesQuery(cleanQuery) || it.body.matchesQuery(cleanQuery) }
                .mapTo(this) {
                    LocalSearchResult(
                        type = ItemDetailType.Note,
                        id = it.id,
                        title = it.title.ifBlank { "Untitled note" },
                        snippet = it.body.ifBlank { it.title },
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = if (it.archived) "Archived" else "Note",
                        timestamp = it.updatedAt,
                    )
                }

            corpus.tasks
                .filter { includeArchived || it.status != TaskStatus.Archived }
                .filter { it.title.matchesQuery(cleanQuery) || it.notes.matchesQuery(cleanQuery) }
                .mapTo(this) {
                    LocalSearchResult(
                        type = ItemDetailType.Task,
                        id = it.id,
                        title = it.title.ifBlank { "Untitled task" },
                        snippet = it.notes.ifBlank { it.status.name },
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = it.status.label(),
                        timestamp = it.updatedAt,
                    )
                }

            corpus.reminders
                .filter { it.title.matchesQuery(cleanQuery) || it.notes.matchesQuery(cleanQuery) }
                .mapTo(this) {
                    LocalSearchResult(
                        type = ItemDetailType.Reminder,
                        id = it.id,
                        title = it.title.ifBlank { "Untitled reminder" },
                        snippet = it.notes.ifBlank { "Reminder" },
                        spaceName = it.spaceId?.let(spacesById::get)?.name,
                        status = if (it.completedAt == null) "Reminder" else "Completed reminder",
                        timestamp = it.dueAt,
                    )
                }
        }.sortedByDescending { it.timestamp }
    }

    private fun String.matchesQuery(query: String): Boolean = contains(query, ignoreCase = true)

    private companion object {
        const val MinQueryLength = 2
    }
}

private fun TaskStatus.label(): String = when (this) {
    TaskStatus.Open -> "Task"
    TaskStatus.Done -> "Done"
    TaskStatus.Archived -> "Archived"
    TaskStatus.WaitingFor -> "Waiting for"
    TaskStatus.Someday -> "Someday"
}
