package com.orbit.app.data.export

import com.orbit.app.data.local.entity.LabelEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.NoteLabelCrossRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDataExporterSnapshotTest {
    @Test
    fun payloadReadsOneInternallyConsistentSnapshot() = runBlocking {
        val snapshot = LocalDataSnapshot(
            spaces = emptyList(),
            captures = emptyList(),
            notes = listOf(NoteEntity(id = 1, title = "Saved note", body = "")),
            tasks = emptyList(),
            reminders = emptyList(),
            labels = listOf(LabelEntity(id = 2, name = "Errand", normalizedName = "errand")),
            noteLabels = listOf(NoteLabelCrossRef(noteId = 1, labelId = 2)),
        )
        val reader = RecordingSnapshotReader(snapshot)

        val decoded = LocalDataBackupCodec.decode(
            buildLocalDataExportPayload(reader, exportedAt = 9_000),
        )

        assertEquals(1, reader.readCount)
        assertEquals(snapshot.labels, decoded.labels)
        assertEquals(snapshot.noteLabels, decoded.noteLabels)
    }
}

private class RecordingSnapshotReader(
    private val snapshot: LocalDataSnapshot,
) : LocalDataSnapshotReader {
    var readCount: Int = 0

    override suspend fun read(): LocalDataSnapshot {
        readCount += 1
        return snapshot
    }
}
