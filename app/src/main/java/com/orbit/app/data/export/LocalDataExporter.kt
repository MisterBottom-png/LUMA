package com.orbit.app.data.export

import android.content.Context
import android.os.Environment
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.data.repository.TaskRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

class LocalDataExporter(
    private val context: Context,
    private val captureRepository: CaptureRepository,
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val spaceRepository: SpaceRepository,
) {
    suspend fun exportJson(): File {
        val exportedAt = System.currentTimeMillis()
        val payload = LocalDataBackupCodec.encode(
            snapshot = LocalDataSnapshot(
                spaces = spaceRepository.observeAll().first(),
                captures = captureRepository.observeAll().first(),
                notes = noteRepository.observeAll().first(),
                tasks = taskRepository.observeAll().first(),
                reminders = reminderRepository.observeAll().first(),
            ),
            exportedAt = exportedAt,
        )

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "exports")
        if (!directory.exists()) directory.mkdirs()

        val file = File(directory, "luma-export-${exportedAt.exportStamp()}.json")
        file.writeText(payload)
        return file
    }

    private fun Long.exportStamp(): String = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
}
