package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.local.entity.LearnedRuleCategory
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.data.local.entity.PersonMemoryEntity
import com.orbit.app.data.local.entity.ProjectMemoryEntity
import com.orbit.app.data.local.entity.SpaceAliasMemoryEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.repository.AiCorrectionHistoryRepository
import com.orbit.app.data.repository.LearnedRuleRepository
import com.orbit.app.data.repository.PersonMemoryRepository
import com.orbit.app.data.repository.ProjectMemoryRepository
import com.orbit.app.data.repository.SpaceAliasMemoryRepository
import com.orbit.app.data.repository.SpaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLearningProfileProviderTest {
    @Test
    fun profileUsesEnabledRelevantRulesAndSkipsDisabledRules() = runBlocking {
        val provider = provider(
            rules = listOf(
                LearnedRuleEntity(
                    id = 1,
                    title = "manager",
                    ruleText = "manager usually maps to Work",
                    category = LearnedRuleCategory.Person,
                    enabled = true,
                    strength = 0.9f,
                ),
                LearnedRuleEntity(
                    id = 2,
                    title = "Money",
                    ruleText = "money worries usually become Reflection in Money",
                    category = LearnedRuleCategory.Type,
                    enabled = false,
                    strength = 1f,
                ),
            ),
        )

        val profile = provider.profileFor("Ask manager about Monday board")

        assertTrue(profile.contains("manager usually maps to Work"))
        assertFalse(profile.contains("money worries"))
    }

    @Test
    fun profileMapsRelevantSpaceAliasesToSpaceNames() = runBlocking {
        val provider = provider(
            spaces = listOf(SpaceEntity(id = 4, name = "Money", icon = "payments", colorAccent = "#62A77A", sortOrder = 0)),
            aliases = listOf(SpaceAliasMemoryEntity(id = 1, spaceId = 4, alias = "salary", enabled = true)),
        )

        val profile = provider.profileFor("salary question")

        assertTrue(profile.contains("alias 'salary' usually maps to Money"))
    }

    @Test
    fun profileCompactsRepeatedCorrectionsWithoutRawHistoryDump() = runBlocking {
        val provider = provider(
            corrections = listOf(
                AiCorrectionHistoryEntity(
                    id = 1,
                    fieldName = "type",
                    originalValue = "Task",
                    correctedValue = "Note",
                    sourceTextSnippet = "money worry about rent",
                ),
                AiCorrectionHistoryEntity(
                    id = 2,
                    fieldName = "type",
                    originalValue = "Task",
                    correctedValue = "Note",
                    sourceTextSnippet = "money worry about salary",
                ),
            ),
        )

        val profile = provider.profileFor("money worries")

        assertTrue(profile.contains("for money"))
        assertTrue(profile.contains("type Task -> Note"))
        assertFalse(profile.contains("rent"))
        assertFalse(profile.contains("salary"))
    }

    private fun provider(
        rules: List<LearnedRuleEntity> = emptyList(),
        people: List<PersonMemoryEntity> = emptyList(),
        projects: List<ProjectMemoryEntity> = emptyList(),
        spaces: List<SpaceEntity> = emptyList(),
        aliases: List<SpaceAliasMemoryEntity> = emptyList(),
        corrections: List<AiCorrectionHistoryEntity> = emptyList(),
    ) = LocalLearningProfileProvider(
        learnedRuleRepository = FakeLearnedRuleRepository(rules),
        personMemoryRepository = FakePersonMemoryRepository(people),
        projectMemoryRepository = FakeProjectMemoryRepository(projects),
        spaceRepository = FakeSpaceRepository(spaces),
        spaceAliasMemoryRepository = FakeSpaceAliasMemoryRepository(aliases),
        correctionHistoryRepository = FakeCorrectionHistoryRepository(corrections),
    )

    private class FakeLearnedRuleRepository(
        private val items: List<LearnedRuleEntity>,
    ) : LearnedRuleRepository {
        override fun observeAll(): Flow<List<LearnedRuleEntity>> = MutableStateFlow(items)
        override fun observeEnabled(): Flow<List<LearnedRuleEntity>> = MutableStateFlow(items.filter { it.enabled })
        override suspend fun getById(id: Long): LearnedRuleEntity? = items.firstOrNull { it.id == id }
        override suspend fun insert(entity: LearnedRuleEntity): Long = entity.id
        override suspend fun update(entity: LearnedRuleEntity) = Unit
        override suspend fun delete(entity: LearnedRuleEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class FakePersonMemoryRepository(
        private val items: List<PersonMemoryEntity>,
    ) : PersonMemoryRepository {
        override fun observeAll(): Flow<List<PersonMemoryEntity>> = MutableStateFlow(items)
        override fun observeEnabled(): Flow<List<PersonMemoryEntity>> = MutableStateFlow(items.filter { it.enabled })
        override suspend fun getById(id: Long): PersonMemoryEntity? = items.firstOrNull { it.id == id }
        override suspend fun insert(entity: PersonMemoryEntity): Long = entity.id
        override suspend fun update(entity: PersonMemoryEntity) = Unit
        override suspend fun delete(entity: PersonMemoryEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class FakeProjectMemoryRepository(
        private val items: List<ProjectMemoryEntity>,
    ) : ProjectMemoryRepository {
        override fun observeAll(): Flow<List<ProjectMemoryEntity>> = MutableStateFlow(items)
        override fun observeEnabled(): Flow<List<ProjectMemoryEntity>> = MutableStateFlow(items.filter { it.enabled })
        override suspend fun getById(id: Long): ProjectMemoryEntity? = items.firstOrNull { it.id == id }
        override suspend fun insert(entity: ProjectMemoryEntity): Long = entity.id
        override suspend fun update(entity: ProjectMemoryEntity) = Unit
        override suspend fun delete(entity: ProjectMemoryEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class FakeSpaceRepository(
        private val items: List<SpaceEntity>,
    ) : SpaceRepository {
        override fun observeAll(): Flow<List<SpaceEntity>> = MutableStateFlow(items)
        override suspend fun getById(id: Long): SpaceEntity? = items.firstOrNull { it.id == id }
        override suspend fun insert(entity: SpaceEntity): Long = entity.id
        override suspend fun update(entity: SpaceEntity) = Unit
        override suspend fun delete(entity: SpaceEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
    }

    private class FakeSpaceAliasMemoryRepository(
        private val items: List<SpaceAliasMemoryEntity>,
    ) : SpaceAliasMemoryRepository {
        override fun observeAll(): Flow<List<SpaceAliasMemoryEntity>> = MutableStateFlow(items)
        override fun observeEnabled(): Flow<List<SpaceAliasMemoryEntity>> = MutableStateFlow(items.filter { it.enabled })
        override suspend fun getById(id: Long): SpaceAliasMemoryEntity? = items.firstOrNull { it.id == id }
        override suspend fun insert(entity: SpaceAliasMemoryEntity): Long = entity.id
        override suspend fun update(entity: SpaceAliasMemoryEntity) = Unit
        override suspend fun delete(entity: SpaceAliasMemoryEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun deleteAll() = Unit
    }

    private class FakeCorrectionHistoryRepository(
        private val items: List<AiCorrectionHistoryEntity>,
    ) : AiCorrectionHistoryRepository {
        override fun observeAll(): Flow<List<AiCorrectionHistoryEntity>> = MutableStateFlow(items)
        override suspend fun getById(id: Long): AiCorrectionHistoryEntity? = items.firstOrNull { it.id == id }
        override suspend fun insert(entity: AiCorrectionHistoryEntity): Long = entity.id
        override suspend fun update(entity: AiCorrectionHistoryEntity) = Unit
        override suspend fun delete(entity: AiCorrectionHistoryEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun deleteAll() = Unit
    }
}
