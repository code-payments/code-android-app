package com.flipcash.app.persistence

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.driver.SupportSQLiteConnection
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

/**
 * Covers the v54 upgrade adding `chat_metadata.archived_at_epoch_ms` and `chat_archive.archived`.
 *
 * Room's auto-migration adds the archive column as NULL and `archived` at its default of 1, so every
 * chat the user archived before the upgrade becomes an archive waiting to upload.
 * [FlipcashDatabase.Migration53To54] then zeroes every `viewer_state_version`, so the next fetch gets
 * past the write gate and fills the archive the server already holds.
 */
@RunWith(RobolectricTestRunner::class)
class ArchiveMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun openV54Shape(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(callback)
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        // The columns the spec touches, as the auto-migration leaves them.
        db.execSQL(
            "CREATE TABLE chat_metadata (chat_id_hex TEXT NOT NULL, " +
                "mute_forever INTEGER NOT NULL DEFAULT 0, " +
                "viewer_state_version INTEGER NOT NULL DEFAULT 0, " +
                "archived_at_epoch_ms INTEGER, PRIMARY KEY(chat_id_hex))"
        )
        return db
    }

    private fun SupportSQLiteDatabase.long(sql: String): Long =
        query(sql).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun `migration 53 to 54 re-arms the viewer state gate and keeps the mute`() {
        val db = openV54Shape()
        db.execSQL(
            "INSERT INTO chat_metadata (chat_id_hex, mute_forever, viewer_state_version) VALUES (?,?,?)",
            arrayOf<Any?>("aa", 1, 7),
        )

        FlipcashDatabase.Migration53To54().onPostMigrate(SupportSQLiteConnection(db))

        assertEquals(0L, db.long("SELECT viewer_state_version FROM chat_metadata"))
        assertEquals(1L, db.long("SELECT mute_forever FROM chat_metadata"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM chat_metadata WHERE archived_at_epoch_ms IS NOT NULL"))

        db.close()
    }

    /** A v53 file built from the exported schema, as an install upgrading to v54 has it. */
    private fun createV53(file: File): SupportSQLiteDatabase {
        val schema = JSONObject(
            File("schemas/com.flipcash.app.persistence.FlipcashDatabase/53.json").readText()
        ).getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(53) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(file.path)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `upgrading to 54 turns each local archive into an archive waiting to upload`() {
        val file = File(context.cacheDir, "archive-migration.db")
        try {
            createV53(file).apply {
                execSQL(
                    "INSERT INTO chat_metadata (chat_id_hex, chat_type, last_activity_epoch_ms, viewer_state_version) " +
                        "VALUES ('aa', 'GROUP', 1, 7)"
                )
                execSQL("INSERT INTO chat_archive (chat_id_hex, archived_at) VALUES ('aa', 1000)")
                close()
            }

            // Opening through Room runs the real auto-migration and its spec.
            val room = Room.databaseBuilder(context, FlipcashDatabase::class.java, file.path)
                .addCallback(ChatPartialIndexes.Callback)
                .allowMainThreadQueries()
                .build()
            val db = room.openHelper.writableDatabase

            assertEquals(54, db.version)
            assertEquals(1L, db.long("SELECT archived FROM chat_archive WHERE chat_id_hex = 'aa'"))
            assertEquals(1000L, db.long("SELECT archived_at FROM chat_archive WHERE chat_id_hex = 'aa'"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM chat_metadata WHERE archived_at_epoch_ms IS NOT NULL"))
            assertEquals(0L, db.long("SELECT viewer_state_version FROM chat_metadata WHERE chat_id_hex = 'aa'"))
            room.close()
        } finally {
            context.deleteDatabase(file.path)
        }
    }
}
