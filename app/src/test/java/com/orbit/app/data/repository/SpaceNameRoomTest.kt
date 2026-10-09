package com.orbit.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.DuplicateSpaceNameException
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.SpaceNames
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.testing.inMemoryOrbitDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpaceNameRoomTest {
    private lateinit var database: OrbitDatabase
    private lateinit var repository: RoomSpaceRepository

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
        repository = RoomSpaceRepository(database.spaceDao())
    }

    @After
    fun tearDown() = database.close()

    private fun space(name: String, archived: Boolean = false, sortOrder: Int = 0) = SpaceEntity(
        name = name,
        icon = "folder",
        colorAccent = "violet",
        sortOrder = sortOrder,
        archived = archived,
    )

    @Test
    fun normalizationTreatsCaseSpacingAndUnicodeFormsAsTheSameName() {
        val composed = "Café"
        val decomposed = "café"
        assertEquals(SpaceNames.normalize(composed), SpaceNames.normalize("  $decomposed  "))
        assertEquals("work list", SpaceNames.normalize("  WORK \t list "))
        assertEquals("Дом", SpaceNames.clean("  Дом "))
        assertEquals(SpaceNames.normalize("ДОМ"), SpaceNames.normalize("дом"))
    }

    @Test
    fun insertRejectsANameAlreadyUsedEvenByAnArchivedSpace() = runBlocking {
        val archivedId = repository.insert(space("Garden", archived = true))

        try {
            repository.insert(space("  garden "))
            fail("A second Garden must not be created")
        } catch (duplicate: DuplicateSpaceNameException) {
            assertEquals(archivedId, duplicate.existing.id)
        }
        assertEquals(1, database.spaceDao().getAll().size)
    }

    @Test
    fun insertStoresTheCleanName() = runBlocking {
        val id = repository.insert(space("  Home   life "))
        assertEquals("Home life", repository.getById(id)?.name)
    }

    @Test
    fun renameToATakenNameFails_butArchivingLegacyDuplicatesStillWorks() = runBlocking {
        val dao = database.spaceDao()
        // An older backup may already contain duplicate names; restore inserts them as-is.
        dao.insertAll(listOf(space("Work", sortOrder = 0), space("work", sortOrder = 1)))
        val (first, second) = dao.getAll()

        repository.update(second.copy(archived = true))
        assertEquals(true, repository.getById(second.id)?.archived)

        val otherId = repository.insert(space("Health", sortOrder = 2))
        try {
            repository.update(repository.getById(otherId)!!.copy(name = "WORK"))
            fail("Renaming onto an existing name must fail")
        } catch (duplicate: DuplicateSpaceNameException) {
            assertNotNull(duplicate.existing)
        }
        assertEquals("Health", repository.getById(otherId)?.name)
        assertEquals("Work", repository.getById(first.id)?.name)
    }

    @Test
    fun starterSpacesSkipTakenNamesAndDuplicatesWithinTheBatch() = runBlocking {
        repository.insert(space("Work"))

        val inserted = repository.insertStarterSpaces(
            listOf(space("work"), space("Home"), space(" home "), space("   ")),
        )

        assertEquals(1, inserted)
        assertEquals(listOf("Work", "Home"), database.spaceDao().getAll().map { it.name })
    }

    @Test
    fun blankNamesAreRejected() = runBlocking {
        try {
            repository.insert(space("   "))
            fail("Blank names must be rejected")
        } catch (_: IllegalArgumentException) {
        }
        assertNull(database.spaceDao().getAll().firstOrNull())
    }
}
