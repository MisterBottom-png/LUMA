package com.orbit.app.data.local

import android.content.Context
import androidx.core.content.edit

/** When this phone last made an export, so Settings can say so. Device-local, not in the export. */
interface LastExportMemory {
    fun lastExportAt(): Long?
    fun recordExport(at: Long)
}

class SharedPreferencesLastExportMemory(context: Context) : LastExportMemory {
    private val preferences = context.applicationContext
        .getSharedPreferences("tallele_local_data_tools", Context.MODE_PRIVATE)

    override fun lastExportAt(): Long? =
        preferences.getLong(LastExportKey, 0L).takeIf { it > 0L }

    override fun recordExport(at: Long) {
        preferences.edit { putLong(LastExportKey, at) }
    }

    private companion object {
        const val LastExportKey = "lastExportAt"
    }
}
