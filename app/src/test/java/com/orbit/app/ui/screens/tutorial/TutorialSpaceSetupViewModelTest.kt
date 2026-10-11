package com.orbit.app.ui.screens.tutorial

import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.repository.SpaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialSpaceSetupViewModelTest {
    @Test
    fun emptyFirstTimeInstall_canSaveSelectedTemplateAndCustomSpace() {
        val repository = FakeSpaceRepository()
        val viewModel = TutorialSpaceSetupViewModel(
            isReplay = false,
            spaceRepository = repository,
            observationDispatcher = Dispatchers.Unconfined,
            workerDispatcher = Dispatchers.Unconfined,
            uiDispatcher = Dispatchers.Unconfined,
        )
        var completed = false

        viewModel.toggleTemplate("home")
        viewModel.addCustomName("  Garden   plans ")
        viewModel.finish { completed = true }

        assertEquals(listOf("Household", "Garden plans"), repository.spaces.value.map { it.name })
        assertEquals(listOf("home", "folder"), repository.spaces.value.map { it.icon })
        assertEquals(listOf(0, 1), repository.spaces.value.map { it.sortOrder })
        assertTrue(completed)
    }

    @Test
    fun replayAndExistingSpaces_neverOfferOrCreateSetupSpaces() {
        val existing = SpaceEntity(name = "Existing", icon = "folder", colorAccent = "#000000", sortOrder = 0)
        val repository = FakeSpaceRepository(initial = listOf(existing))
        val viewModel = TutorialSpaceSetupViewModel(
            isReplay = true,
            spaceRepository = repository,
            observationDispatcher = Dispatchers.Unconfined,
            workerDispatcher = Dispatchers.Unconfined,
            uiDispatcher = Dispatchers.Unconfined,
        )
        var completed = false

        assertFalse(viewModel.uiState.value.canConfigure)
        viewModel.toggleTemplate("home")
        viewModel.finish { completed = true }

        assertEquals(listOf(existing), repository.spaces.value)
        assertTrue(completed)
    }

    @Test
    fun blankAndDuplicateCustomNames_areIgnored() {
        val viewModel = TutorialSpaceSetupViewModel(
            isReplay = false,
            spaceRepository = FakeSpaceRepository(),
            observationDispatcher = Dispatchers.Unconfined,
            workerDispatcher = Dispatchers.Unconfined,
            uiDispatcher = Dispatchers.Unconfined,
        )

        viewModel.addCustomName("   ")
        viewModel.addCustomName("Garden")
        viewModel.addCustomName(" garden ")

        assertEquals(listOf("Garden"), viewModel.uiState.value.customNames)
    }

    private class FakeSpaceRepository(initial: List<SpaceEntity> = emptyList()) : SpaceRepository {
        val spaces = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<SpaceEntity>> = spaces
        override suspend fun getById(id: Long): SpaceEntity? = spaces.value.firstOrNull { it.id == id }
        override suspend fun insert(entity: SpaceEntity): Long {
            val id = (spaces.value.maxOfOrNull { it.id } ?: 0L) + 1
            spaces.value = spaces.value + entity.copy(id = id)
            return id
        }
        override suspend fun update(entity: SpaceEntity) {
            spaces.value = spaces.value.map { if (it.id == entity.id) entity else it }
        }
        override suspend fun delete(entity: SpaceEntity) = deleteById(entity.id)
        override suspend fun deleteById(id: Long) {
            spaces.value = spaces.value.filterNot { it.id == id }
        }
    }
}

class TutorialPrimaryLabelTest {
    @Test
    fun replayingTheGuideEndsWithDone() {
        org.junit.Assert.assertEquals(com.orbit.app.R.string.tutorial_done, tutorialPrimaryLabel(isLastPage = true, isReplay = true))
        org.junit.Assert.assertEquals(com.orbit.app.R.string.tutorial_start, tutorialPrimaryLabel(isLastPage = true, isReplay = false))
        org.junit.Assert.assertEquals(com.orbit.app.R.string.tutorial_continue, tutorialPrimaryLabel(isLastPage = false, isReplay = true))
    }
}
