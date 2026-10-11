package com.orbit.app.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.entity.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchivedTaskStatusMemoryTest {
    @Test
    fun clearingForgetsEveryTaskSoRestoredIdsStartFresh() {
        val memory = SharedPreferencesArchivedTaskStatusMemory(ApplicationProvider.getApplicationContext())
        memory.remember(7, TaskStatus.WaitingFor)
        assertEquals(TaskStatus.WaitingFor, memory.recall(7))

        memory.clear()

        assertNull(memory.recall(7))
    }
}
