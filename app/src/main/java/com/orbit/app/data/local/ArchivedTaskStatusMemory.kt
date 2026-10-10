package com.orbit.app.data.local

import android.content.Context
import androidx.core.content.edit
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus

/**
 * Remembers, on this device, a task's status from before it was archived, so restoring
 * it brings back "Waiting for" or "Someday" instead of always "Open". Kept outside the
 * database (no schema change); after a backup restore a done task still comes back as
 * Done (its completion time is kept), any other as Open.
 */
interface ArchivedTaskStatusMemory {
    fun remember(taskId: Long, status: TaskStatus)
    fun recall(taskId: Long): TaskStatus?
    fun forget(taskId: Long)

    object None : ArchivedTaskStatusMemory {
        override fun remember(taskId: Long, status: TaskStatus) = Unit
        override fun recall(taskId: Long): TaskStatus? = null
        override fun forget(taskId: Long) = Unit
    }
}

class SharedPreferencesArchivedTaskStatusMemory(context: Context) : ArchivedTaskStatusMemory {
    private val preferences = context.applicationContext
        .getSharedPreferences("tallele_archived_task_status", Context.MODE_PRIVATE)

    override fun remember(taskId: Long, status: TaskStatus) {
        if (status == TaskStatus.Archived) return
        preferences.edit { putString(taskId.toString(), status.name) }
    }

    override fun recall(taskId: Long): TaskStatus? = preferences.getString(taskId.toString(), null)
        ?.let { name -> TaskStatus.entries.firstOrNull { it.name == name } }

    override fun forget(taskId: Long) {
        preferences.edit { remove(taskId.toString()) }
    }
}

/** The status an archived task returns to when restored. */
fun TaskEntity.statusAfterRestore(memory: ArchivedTaskStatusMemory): TaskStatus =
    memory.recall(id)?.takeUnless { it == TaskStatus.Archived }
        ?: if (completedAt != null) TaskStatus.Done else TaskStatus.Open
