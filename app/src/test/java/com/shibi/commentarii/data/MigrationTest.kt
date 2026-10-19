package com.shibi.commentarii.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a version 1 database from the exported schema (`app/schemas/.../1.json`, tests run in the module directory),
 * opens it with Room and `MIGRATION_1_2`, and checks the (type, date) index.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var db: CommentariiDatabase? = null

    private val day = LocalDate.of(2026, 10, 12).toEpochDay()

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(DB)
    }

    private fun createVersion1() {
        val schema = JSONObject(File("schemas/${CommentariiDatabase::class.java.name}/1.json").readText())
            .getJSONObject("database")
        val file = context.getDatabasePath(DB).also { it.parentFile?.mkdirs() }
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
        insertPage(sqlite, 1, PageType.DAILY, day)
        insertPage(sqlite, 2, PageType.NOTE, null)
        sqlite.execSQL("INSERT INTO moods (date, mood) VALUES ($day, 4)")
        sqlite.version = 1
        sqlite.close()
    }

    private fun sql(id: Long, type: String, date: Long?) =
        "INSERT INTO pages (id, type, title, date, folderId, pinned, createdAt, updatedAt, lastOpenedAt) " +
            "VALUES ($id, '$type', 'T$id', ${date ?: "NULL"}, NULL, 0, 1, 1, NULL)"

    private fun insertPage(sqlite: SQLiteDatabase, id: Long, type: String, date: Long?) = sqlite.execSQL(sql(id, type, date))

    private fun insertPage(sqlite: SupportSQLiteDatabase, id: Long, type: String, date: Long?) =
        sqlite.execSQL(sql(id, type, date))

    @Test
    fun migration1To2KeepsDataAndAllowsSameDateAcrossTypes() {
        createVersion1()

        val database = Room.databaseBuilder(context, CommentariiDatabase::class.java, DB)
            .addMigrations(CommentariiDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        db = database
        val sqlite = database.openHelper.writableDatabase

        sqlite.query("SELECT id, type, date FROM pages ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals(PageType.DAILY, c.getString(1))
            assertEquals(day, c.getLong(2))
            c.moveToNext()
            assertEquals(PageType.NOTE, c.getString(1))
        }
        sqlite.query("SELECT mood FROM moods WHERE date = $day").use { c ->
            c.moveToFirst()
            assertEquals(4, c.getInt(0))
        }
        sqlite.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'pages' ORDER BY name").use { c ->
            val names = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
            assertEquals(
                listOf("index_pages_folderId", "index_pages_type", "index_pages_type_date"),
                names.filter { it.startsWith("index_pages_") },
            )
        }

        // A weekly page may now share the daily page's date; the same (type, date) twice still fails.
        insertPage(sqlite, 3, PageType.WEEKLY, day)
        try {
            insertPage(sqlite, 4, PageType.WEEKLY, day)
            fail("expected a unique constraint violation")
        } catch (_: SQLiteConstraintException) {
        }
        try {
            insertPage(sqlite, 5, PageType.DAILY, day)
            fail("expected a unique constraint violation")
        } catch (_: SQLiteConstraintException) {
        }
        // Notes have no date and may repeat.
        insertPage(sqlite, 6, PageType.NOTE, null)
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
