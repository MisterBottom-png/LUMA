package com.orbit.app.ui.screens.spaces

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.OrbitContainer
import com.orbit.app.data.local.entity.SpaceEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Hide or Archive from a Space's own page closes that page at once; the change must still be saved. */
@RunWith(AndroidJUnit4::class)
class SpaceHideOutlivesScreenTest {
    @Test
    fun hidingThenLeavingTheSpacePageStillHidesIt() = runBlocking {
        val container = OrbitContainer(ApplicationProvider.getApplicationContext<Application>())
        val spaceId = container.spaceRepository.insert(
            SpaceEntity(name = "Garden", icon = "home", colorAccent = "#000000", sortOrder = 0),
        )
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, SpacesViewModel.Factory(container))[SpacesViewModel::class.java]

        viewModel.hideSpace(spaceId)
        store.clear() // the page closes, as it does after Hide

        val hidden = withTimeoutOrNull(5_000) {
            while (container.spaceRepository.getById(spaceId)?.hidden != true) delay(20)
            true
        }
        assertEquals(true, hidden)
        container.database.close()
    }
}
