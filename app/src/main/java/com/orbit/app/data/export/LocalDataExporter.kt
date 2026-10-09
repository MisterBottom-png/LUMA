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
): String = LocalDataBackupCodec.encode(
    snapshot = snapshotReader.read(),
    exportedAt = exportedAt,
)
