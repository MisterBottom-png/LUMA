package com.orbit.app.testing

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orbit.app.data.local.OrbitDatabase
import java.io.File
import org.json.JSONObject

/**
 * Creates an on-disk database exactly as an older app version would have, from the
 * exported Room schema JSON, so JVM tests can run the real migration chain.
 */
object SchemaDatabases {
    fun createAtVersion(name: String, version: Int, seed: (SQLiteDatabase) -> Unit = {}): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val schema = JSONObject(schemaFile(version).readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (i in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
            seed(db)
            db.version = version
        }
        return file
    }

    /** Opens the database with every production migration; Room validates the result. */
    fun openMigrated(name: String): OrbitDatabase = Room
        .databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OrbitDatabase::class.java, name)
        .addMigrations(*OrbitDatabase.AllMigrations)
        .allowMainThreadQueries()
        .build()

    private fun schemaFile(version: Int): File {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { it.resolve("app/schemas").isDirectory }
        return root.resolve("app/schemas/com.orbit.app.data.local.OrbitDatabase/$version.json")
    }
}
