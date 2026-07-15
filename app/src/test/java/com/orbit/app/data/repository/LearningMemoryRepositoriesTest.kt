package com.orbit.app.data.repository

import com.orbit.app.data.local.dao.LearnedRuleDao
import com.orbit.app.data.local.entity.LearnedRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearningMemoryRepositoriesTest {
    @Test
    fun learnedRuleRepositoryExposesEnabledRulesAndReset() = runBlocking {
        val dao = FakeLearnedRuleDao(
            initial = listOf(
                LearnedRuleEntity(id = 1, title = "Use Money", ruleText = "Route salary to Money", strength = 0.8f),
                LearnedRuleEntity(
                    id = 2,
                    title = "Old rule",
                    ruleText = "Disabled",
                    enabled = false,
                    strength = 0.9f,
                ),
            ),
        )
        val repository = RoomLearnedRuleRepository(dao)

        assertEquals(listOf(1L), repository.observeEnabled().first().map { it.id })

        repository.deleteAll()

        assertEquals(emptyList<LearnedRuleEntity>(), repository.observeAll().first())
        assertNull(repository.getById(1))
    }

    private class FakeLearnedRuleDao(
        initial: List<LearnedRuleEntity>,
    ) : LearnedRuleDao {
        private val items = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<LearnedRuleEntity>> = items

        override fun observeEnabled(): Flow<List<LearnedRuleEntity>> =
            MutableStateFlow(items.value.filter { it.enabled }.sortedByDescending { it.strength })

        override suspend fun getById(id: Long): LearnedRuleEntity? =
            items.value.firstOrNull { it.id == id }

        override suspend fun insert(entity: LearnedRuleEntity): Long {
            val nextId = entity.id.takeIf { it != 0L }
                ?: ((items.value.maxOfOrNull { it.id } ?: 0L) + 1L)
            items.value = items.value + entity.copy(id = nextId)
            return nextId
        }

        override suspend fun update(entity: LearnedRuleEntity) {
            items.value = items.value.map { if (it.id == entity.id) entity else it }
        }

        override suspend fun delete(entity: LearnedRuleEntity) {
            deleteById(entity.id)
        }

        override suspend fun deleteById(id: Long) {
            items.value = items.value.filterNot { it.id == id }
        }

        override suspend fun deleteAll() {
            items.value = emptyList()
        }
    }
}
