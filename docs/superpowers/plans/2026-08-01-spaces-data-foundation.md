# Spaces Data Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the transactionally safe local label persistence and versioned backup foundation required by Calm Area Hubs without changing the current Spaces UI.

**Architecture:** Room version 6 adds one normalized label table and three type-safe item-label join tables. One DAO owns label lookup and atomic replacement of an item's labels; the repository exposes that DAO without weakening foreign-key integrity. Local export format 4 serializes labels and relations, validates every reference before restore, and imports older formats with empty label collections.

**Tech Stack:** Kotlin, Room, coroutines and Flow, AndroidX Room migration testing, JUnit 4, `org.json`, Gradle.

**Milestone:** This is plan 1 of 3. It deliberately excludes capture/onboarding integration and the Calm Area Hubs UI; those follow as separate plans after this persistence contract is green.

## Global Constraints

- Preserve every existing Space, capture, note, task, reminder, assignment, and Brain Dump record through migration.
- Raw captures and internal AI records remain outside Spaces and cannot receive item labels.
- Do not change reminder target time, notification offset, event time, or 24-hour behavior.
- Keep export/restore local, validated before replacement, and transactional.
- Do not add production dependencies.
- Do not initialize or repair Git metadata as part of this feature; run commit steps only if the workspace is recognized as a Git repository.
- Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes.

---

## File structure

- `app/src/main/java/com/orbit/app/data/local/entity/OrbitEntities.kt`: declare `LabelEntity` and the three foreign-key join entities.
- `app/src/main/java/com/orbit/app/data/local/dao/OrbitDaos.kt`: own label observation, lookup, inserts, deletes, and atomic per-item relationship replacement.
- `app/src/main/java/com/orbit/app/data/local/OrbitDatabase.kt`: register the entities and DAO, bump Room to version 6, and define migration 5 to 6.
- `app/src/main/java/com/orbit/app/data/repository/EntityRepositories.kt`: expose label operations through the existing repository boundary.
- `app/src/main/java/com/orbit/app/OrbitApplication.kt`: wire the label repository into `OrbitContainer`.
- `app/src/main/java/com/orbit/app/data/export/LocalDataBackupCodec.kt`: define export-format-4 fields, JSON encoding/decoding, and reference validation.
- `app/src/main/java/com/orbit/app/data/export/LocalDataExporter.kt`: include labels and relationships in snapshots.
- `app/src/main/java/com/orbit/app/data/export/LocalDataRestorer.kt`: read and transactionally replace labels and relationships in dependency-safe order.
- `app/src/androidTest/java/com/orbit/app/data/local/OrbitDatabaseMigrationTest.kt`: prove all supported schema versions reach version 6 without data loss.
- `app/src/androidTest/java/com/orbit/app/data/repository/LabelRoomRepositoryTest.kt`: prove normalized lookup, atomic replacement, and cascade behavior.
- `app/src/test/java/com/orbit/app/data/export/LocalDataRestoreTest.kt`: prove format-4 round trips, legacy defaults, invalid-reference rejection, and restore replacement.
- `app/schemas/com.orbit.app.data.local.OrbitDatabase/6.json`: Room-generated schema artifact produced by compilation.

### Task 1: Room version 6 label schema

**Files:**
- Modify: `app/src/main/java/com/orbit/app/data/local/entity/OrbitEntities.kt`
- Modify: `app/src/main/java/com/orbit/app/data/local/dao/OrbitDaos.kt`
- Modify: `app/src/main/java/com/orbit/app/data/local/OrbitDatabase.kt`
- Modify: `app/src/androidTest/java/com/orbit/app/data/local/OrbitDatabaseMigrationTest.kt`
- Generate: `app/schemas/com.orbit.app.data.local.OrbitDatabase/6.json`

**Interfaces:**
- Produces: `LabelEntity`, `NoteLabelCrossRef`, `TaskLabelCrossRef`, `ReminderLabelCrossRef`, `LabelDao`, and `OrbitDatabase.labelDao()`.
- Produces: `OrbitDatabase.Migration5To6: Migration` and Room schema version `6`.

- [x] **Step 1: Write the failing migration test**

Add a version-5-to-6 test that inserts an existing Space and finalized item, migrates, verifies the original rows, and verifies all four new tables are empty:

```kotlin
@Test
fun migrate5To6PreservesFinalizedItemsAndCreatesLabelTables() {
    helper.createDatabase(TEST_DATABASE_5_TO_6, 5).apply {
        execSQL("INSERT INTO spaces (id, name, icon, colorAccent, sortOrder, hidden, archived, createdAt, updatedAt) VALUES (1, 'Home', 'home', '#6D7CFF', 0, 0, 0, 1000, 1000)")
        execSQL("INSERT INTO notes (id, title, body, spaceId, createdAt, updatedAt, archived, scheduledDateEpochDay, scheduledAt) VALUES (1, 'Saved note', '', 1, 1000, 1000, 0, NULL, NULL)")
        close()
    }

    val migrated = helper.runMigrationsAndValidate(
        TEST_DATABASE_5_TO_6,
        6,
        true,
        OrbitDatabase.Migration5To6,
    )

    migrated.query("SELECT title, spaceId FROM notes WHERE id = 1").use {
        assertEquals(true, it.moveToFirst())
        assertEquals("Saved note", it.getString(0))
        assertEquals(1L, it.getLong(1))
    }
    listOf("labels", "note_labels", "task_labels", "reminder_labels").forEach { table ->
        migrated.query("SELECT COUNT(*) FROM $table").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }
    migrated.close()
}
```

Update the supported-start-version test to migrate `(1..5)` to `6`, and add `Migration5To6` to `migrationsFrom` for every start version.

- [x] **Step 2: Compile the instrumentation test to verify RED**

Run: `./gradlew --no-daemon :app:compileDebugAndroidTestKotlin`

Expected: FAIL because `Migration5To6` and the version-6 schema do not exist.

- [x] **Step 3: Add the four Room entities**

Add these entity contracts to `OrbitEntities.kt`:

```kotlin
@Entity(
    tableName = "labels",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class LabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "note_labels",
    primaryKeys = ["noteId", "labelId"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LabelEntity::class, parentColumns = ["id"], childColumns = ["labelId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("labelId")],
)
data class NoteLabelCrossRef(val noteId: Long, val labelId: Long)
```

Add equivalent `TaskLabelCrossRef(taskId, labelId)` in `task_labels` and `ReminderLabelCrossRef(reminderId, labelId)` in `reminder_labels`, each with cascade foreign keys and an index on `labelId`.

- [x] **Step 4: Add the DAO contract**

Declare `LabelDao` with the exact persistence surface needed by repository and backup work:

```kotlin
@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY normalizedName, id")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels ORDER BY normalizedName, id")
    suspend fun getAll(): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): LabelEntity?

    @Insert
    suspend fun insert(entity: LabelEntity): Long

    @Insert
    suspend fun insertAll(entities: List<LabelEntity>)

    @Update
    suspend fun update(entity: LabelEntity)

    @Delete
    suspend fun delete(entity: LabelEntity)

    @Query("SELECT * FROM note_labels ORDER BY noteId, labelId")
    suspend fun getAllNoteLabels(): List<NoteLabelCrossRef>

    @Query("SELECT * FROM task_labels ORDER BY taskId, labelId")
    suspend fun getAllTaskLabels(): List<TaskLabelCrossRef>

    @Query("SELECT * FROM reminder_labels ORDER BY reminderId, labelId")
    suspend fun getAllReminderLabels(): List<ReminderLabelCrossRef>

    @Insert
    suspend fun insertNoteLabels(relations: List<NoteLabelCrossRef>)

    @Insert
    suspend fun insertTaskLabels(relations: List<TaskLabelCrossRef>)

    @Insert
    suspend fun insertReminderLabels(relations: List<ReminderLabelCrossRef>)

    @Query("DELETE FROM note_labels WHERE noteId = :noteId")
    suspend fun deleteNoteLabels(noteId: Long)

    @Query("DELETE FROM task_labels WHERE taskId = :taskId")
    suspend fun deleteTaskLabels(taskId: Long)

    @Query("DELETE FROM reminder_labels WHERE reminderId = :reminderId")
    suspend fun deleteReminderLabels(reminderId: Long)

    @Query("DELETE FROM note_labels")
    suspend fun deleteAllNoteLabels()

    @Query("DELETE FROM task_labels")
    suspend fun deleteAllTaskLabels()

    @Query("DELETE FROM reminder_labels")
    suspend fun deleteAllReminderLabels()

    @Query("DELETE FROM labels")
    suspend fun deleteAllLabels()
}
```

- [x] **Step 5: Register version 6 and implement migration 5 to 6**

Register the four entities, expose `abstract fun labelDao(): LabelDao`, set `version = 6`, and add the migration to the builder. The migration creates `labels` with a unique `normalizedName` index and the three composite-primary-key join tables with cascade foreign keys and `labelId` indices.

Use table and index names identical to the entity declarations so `MigrationTestHelper` validates the generated schema.

- [x] **Step 6: Compile and verify GREEN**

Run: `./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`

Expected: PASS and generate `app/schemas/com.orbit.app.data.local.OrbitDatabase/6.json`.

- [ ] **Step 7: Commit the schema slice when Git is available**

```bash
git add app/src/main/java/com/orbit/app/data/local/entity/OrbitEntities.kt app/src/main/java/com/orbit/app/data/local/dao/OrbitDaos.kt app/src/main/java/com/orbit/app/data/local/OrbitDatabase.kt app/src/androidTest/java/com/orbit/app/data/local/OrbitDatabaseMigrationTest.kt app/schemas/com.orbit.app.data.local.OrbitDatabase/6.json
git commit -m "feat: add local label schema"
```

### Task 2: Transactional label repository

**Files:**
- Modify: `app/src/main/java/com/orbit/app/data/local/dao/OrbitDaos.kt`
- Modify: `app/src/main/java/com/orbit/app/data/repository/EntityRepositories.kt`
- Modify: `app/src/main/java/com/orbit/app/OrbitApplication.kt`
- Create: `app/src/androidTest/java/com/orbit/app/data/repository/LabelRoomRepositoryTest.kt`

**Interfaces:**
- Consumes: `LabelEntity`, the three cross-reference types, and `LabelDao` from Task 1.
- Produces: `LabelRepository`, `RoomLabelRepository`, `normalizeLabelName(String): String`, and `OrbitContainer.labelRepository`.

- [x] **Step 1: Write failing repository tests**

Use an in-memory `OrbitDatabase` and real DAO. Cover these separate behaviors:

```kotlin
@Test
fun findOrCreateReusesWhitespaceAndCaseEquivalentLabel() = runTest {
    val first = repository.findOrCreate("  Errand  ")
    val second = repository.findOrCreate("errand")
    assertEquals(first.id, second.id)
    assertEquals("Errand", first.name)
}

@Test
fun replaceNoteLabelsAtomicallyReplacesOnlyThatNotesRelations() = runTest {
    val first = repository.findOrCreate("Errand")
    val second = repository.findOrCreate("Phone")
    repository.replaceNoteLabels(noteId = 1, labelIds = setOf(first.id, second.id))
    repository.replaceNoteLabels(noteId = 1, labelIds = setOf(second.id))
    assertEquals(listOf(NoteLabelCrossRef(1, second.id)), database.labelDao().getAllNoteLabels())
}
```

Add equivalent relationship replacement checks for a task and reminder, plus a test that deleting a label cascades only its relations and preserves the finalized items.

- [x] **Step 2: Run the repository test to verify RED**

Run: `./gradlew --no-daemon :app:compileDebugAndroidTestKotlin`

Expected: FAIL because `LabelRepository`, `RoomLabelRepository`, and the transactional replacement methods do not exist.

- [x] **Step 3: Add atomic DAO replacement methods**

Add `@Transaction` methods that delete an item's old relations and insert sorted, de-duplicated replacements:

```kotlin
@Transaction
suspend fun replaceNoteLabels(noteId: Long, labelIds: Set<Long>) {
    deleteNoteLabels(noteId)
    insertNoteLabels(labelIds.sorted().map { labelId -> NoteLabelCrossRef(noteId, labelId) })
}
```

Add equivalent task and reminder methods. Empty sets must delete old relations and insert nothing.

- [x] **Step 4: Add repository normalization and operations**

Use one locale-independent normalizer:

```kotlin
internal fun normalizeLabelName(value: String): String = value
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)
```

`findOrCreate(name)` rejects a blank normalized value, returns an existing normalized match, or inserts `LabelEntity(name = cleanedDisplayName, normalizedName = normalized)`. Handle a unique-index race by re-reading the normalized row after a failed insert; rethrow if no row exists.

Expose observation, lookup, deletion, and the three replacement functions through `LabelRepository`. Wire `RoomLabelRepository(database.labelDao())` into `OrbitContainer`.

- [x] **Step 5: Compile and verify GREEN**

Run: `./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`

Expected: PASS.

- [ ] **Step 6: Commit the repository slice when Git is available**

```bash
git add app/src/main/java/com/orbit/app/data/local/dao/OrbitDaos.kt app/src/main/java/com/orbit/app/data/repository/EntityRepositories.kt app/src/main/java/com/orbit/app/OrbitApplication.kt app/src/androidTest/java/com/orbit/app/data/repository/LabelRoomRepositoryTest.kt
git commit -m "feat: add transactional label repository"
```

### Task 3: Export format 4 and transactional restore

**Files:**
- Modify: `app/src/main/java/com/orbit/app/data/export/LocalDataBackupCodec.kt`
- Modify: `app/src/main/java/com/orbit/app/data/export/LocalDataExporter.kt`
- Modify: `app/src/main/java/com/orbit/app/data/export/LocalDataRestorer.kt`
- Modify: `app/src/test/java/com/orbit/app/data/export/LocalDataRestoreTest.kt`
- Modify: `app/src/androidTest/java/com/orbit/app/data/export/LocalDataRestoreRoomTest.kt`

**Interfaces:**
- Consumes: label entities, join entities, and `OrbitDatabase.labelDao()` from Tasks 1 and 2.
- Produces: `LocalDataBackupCodec.Version == 4` and four new `LocalDataSnapshot` collections.

- [x] **Step 1: Write failing codec tests**

Extend the snapshot fixture with one label and one relation per finalized type. Assert that format 4 round-trips all four collections:

```kotlin
val decoded = LocalDataBackupCodec.decode(LocalDataBackupCodec.encode(original, exportedAt = 9_000))
assertEquals(original.labels, decoded.labels)
assertEquals(original.noteLabels, decoded.noteLabels)
assertEquals(original.taskLabels, decoded.taskLabels)
assertEquals(original.reminderLabels, decoded.reminderLabels)
```

Add a legacy-version test asserting versions 1 through 3 decode with all label collections empty. Add one invalid-reference test per relation type, expecting `LocalDataValidationException` before replacement.

- [x] **Step 2: Run focused JVM tests to verify RED**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests com.orbit.app.data.export.LocalDataRestoreTest`

Expected: FAIL because format 4 and the label snapshot fields do not exist.

- [x] **Step 3: Extend snapshot and JSON codec**

Append defaulted fields so existing fixture call sites remain source-compatible:

```kotlin
val labels: List<LabelEntity> = emptyList(),
val noteLabels: List<NoteLabelCrossRef> = emptyList(),
val taskLabels: List<TaskLabelCrossRef> = emptyList(),
val reminderLabels: List<ReminderLabelCrossRef> = emptyList(),
```

Set `Version = 4`. Encode `labels`, `noteLabels`, `taskLabels`, and `reminderLabels` arrays. Decode them only for version 4 or later; versions 1 through 3 use empty lists.

Validate unique label IDs and normalized names. For every relation, require both the finalized item ID and label ID to exist. Reject duplicate `(itemId, labelId)` pairs.

- [x] **Step 4: Include labels in export and restore snapshots**

`LocalDataExporter` reads all labels and relationships through `LabelRepository` or `LabelDao` supplied explicitly to the exporter constructor. `RoomLocalDataRestoreStore.read()` includes all four collections inside its existing transaction.

During replacement, delete join rows before finalized items and labels. Insert in this dependency order:

1. Spaces and captures.
2. Brain Dump sessions and items.
3. Notes, tasks, and reminders.
4. Labels.
5. Note, task, and reminder label relations.

Keep reminder reconciliation after the database replacement, unchanged.

- [x] **Step 5: Add a Room restore relationship test**

Restore a snapshot containing one label attached to a note, task, and reminder. Read the database and assert exact label and relation equality. Perform a second replacement with empty label collections and assert the old labels and relations are gone while the replacement finalized items remain.

- [x] **Step 6: Run focused tests and compile instrumentation tests**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests com.orbit.app.data.export.LocalDataRestoreTest :app:compileDebugAndroidTestKotlin`

Expected: PASS.

- [ ] **Step 7: Commit the backup slice when Git is available**

```bash
git add app/src/main/java/com/orbit/app/data/export/LocalDataBackupCodec.kt app/src/main/java/com/orbit/app/data/export/LocalDataExporter.kt app/src/main/java/com/orbit/app/data/export/LocalDataRestorer.kt app/src/test/java/com/orbit/app/data/export/LocalDataRestoreTest.kt app/src/androidTest/java/com/orbit/app/data/export/LocalDataRestoreRoomTest.kt
git commit -m "feat: preserve labels in local backup"
```

### Task 4: Data-foundation regression gate

**Files:**
- Modify only if a failure is caused by this milestone.

**Interfaces:**
- Consumes: all Tasks 1 through 3.
- Produces: verified Room version 6 and local export format 4 foundation for later Spaces UI and capture plans.

- [x] **Step 1: Run the focused data suites**

Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.orbit.app.data.export.LocalDataRestoreTest --tests com.orbit.app.data.local.StarterSpacesTest :app:assembleDebugAndroidTest
```

Expected: PASS. Instrumentation tests are compiled and packaged; execution still requires a connected target.

- [x] **Step 2: Run the affected module gate**

Run: `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`

Expected: PASS with no new lint errors.

- [x] **Step 3: Run repository text and diff checks**

Run:

```bash
python scripts/codex/check_workplace_privacy.py --strict
git diff --check
git diff -- app/src/main app/src/test app/src/androidTest app/schemas docs/superpowers
```

Expected: privacy heuristic passes; whitespace check passes when Git metadata is available; review shows only the approved design, plan, Room version 6, export format 4, and tests.

- [x] **Step 4: Record the milestone result**

Update `docs/codex/PROJECT_STATE.md` only after the automated evidence is current. Record Room schema version 6, local export format 4 with versions 1 through 4 accepted, and the exact remaining device-test gap. Do not claim device execution from compilation evidence.

- [ ] **Step 5: Commit the evidence update when Git is available**

```bash
git add docs/codex/PROJECT_STATE.md docs/superpowers/plans/2026-08-01-spaces-data-foundation.md docs/superpowers/specs/2026-08-01-calm-area-hubs-spaces-design.md
git commit -m "docs: record Spaces data foundation"
```
