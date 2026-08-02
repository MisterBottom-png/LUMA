package com.orbit.app.data.export

import android.content.Context
import android.net.Uri
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.BrainDumpRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.first

class LocalDataExporter(
    private val context: Context,
    private val captureRepository: CaptureRepository,
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val spaceRepository: SpaceRepository,
    private val brainDumpRepository: BrainDumpRepository,
) {
    suspend fun exportJson(destination: Uri) {
        val exportedAt = System.currentTimeMillis()
        val brainDumpSessions = brainDumpRepository.getAllSessions()
        val payload = LocalDataBackupCodec.encode(
            snapshot = LocalDataSnapshot(
                spaces = spaceRepository.observeAll().first(),
                captures = captureRepository.observeAll().first(),
                notes = noteRepository.observeAll().first(),
                tasks = taskRepository.observeAll().first(),
                reminders = reminderRepository.observeAll().first(),
                brainDumpSessions = brainDumpSessions.map { it.session },
                brainDumpItems = brainDumpSessions.flatMap { it.items },
            ),
            exportedAt = exportedAt,
        )

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
