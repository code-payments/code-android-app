package com.flipcash.app.persistence

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.driver.SupportSQLiteConnection
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}
