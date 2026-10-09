package com.orbit.app.data.export

import android.content.Context
import android.net.Uri

class LocalDataExporter(
    private val context: Context,
    private val snapshotReader: LocalDataSnapshotReader,
) {
    suspend fun exportJson(destination: Uri) {
        val exportedAt = System.currentTimeMillis()
        val payload = buildLocalDataExportPayload(snapshotReader, exportedAt)

        val resolver = context.contentResolver
        try {
            val output = requireNotNull(resolver.openOutputStream(destination, "wt")) {
                "The selected export destination could not be opened."
            }
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }
        } catch (exception: Exception) {
            runCatching { resolver.delete(destination, null, null) }
            throw exception
        }
    }
}

internal suspend fun buildLocalDataExportPayload(
    snapshotReader: LocalDataSnapshotReader,
    exportedAt: Long,
): String {
    val payload = LocalDataBackupCodec.encode(
        snapshot = snapshotReader.read().sanitizedForExport(),
        exportedAt = exportedAt,
    )
    // A backup is only useful if it restores. Verify it with the exact validator
    // restore uses before anything is written to the destination file.
    try {
        LocalDataBackupCodec.decode(payload)
    } catch (exception: LocalDataValidationException) {
        throw LocalDataExportVerificationException(exception.message.orEmpty())
    }
    return payload
}

class LocalDataExportVerificationException(message: String) : IllegalStateException(message)

/**
 * Repairs rows that older versions could store but restore rejects, so an export
 * taken today is always restorable. The local database itself is left unchanged.
 */
internal fun LocalDataSnapshot.sanitizedForExport(): LocalDataSnapshot {
    val activeSessionIds = brainDumpItems
        .filter { it.outcome == com.orbit.app.data.local.entity.BrainDumpItemOutcome.Pending }
        .mapTo(hashSetOf()) { it.captureId }
    return copy(
        notes = notes.map { note ->
            if (note.title.isNotBlank()) note else note.copy(title = derivedExportTitle(note.body))
        },
        tasks = tasks.map { task ->
            if (task.title.isNotBlank()) task else task.copy(title = derivedExportTitle(task.notes))
        },
        reminders = reminders.map { reminder ->
            if (reminder.title.isNotBlank()) reminder else reminder.copy(title = derivedExportTitle(reminder.notes))
        },
        captures = captures.filter { it.rawText.isNotBlank() },
        brainDumpSessions = brainDumpSessions.filter { it.captureId in activeSessionIds },
        brainDumpItems = brainDumpItems.filter { it.captureId in activeSessionIds },
    ).let { sanitized ->
        val captureIds = sanitized.captures.mapTo(hashSetOf()) { it.id }
        sanitized.copy(
            reminders = sanitized.reminders.map { reminder ->
                if (reminder.linkedCaptureId != null && reminder.linkedCaptureId !in captureIds) {
                    reminder.copy(linkedCaptureId = null)
                } else {
                    reminder
                }
            },
            brainDumpSessions = sanitized.brainDumpSessions.filter { it.captureId in captureIds },
            brainDumpItems = sanitized.brainDumpItems.filter { it.captureId in captureIds },
            captureSuggestions = sanitized.captureSuggestions
                .filter { it.captureId in captureIds && it.suggestedTitle.isNotBlank() },
        )
    }
}

internal fun derivedExportTitle(body: String): String =
    deriveItemTitle(body) ?: UntitledExportTitle

/** First meaningful line of a text, shortened; null when the text is blank. */
fun deriveItemTitle(text: String, maxLength: Int = 80): String? =
    text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.take(maxLength)

private const val UntitledExportTitle = "Untitled"
