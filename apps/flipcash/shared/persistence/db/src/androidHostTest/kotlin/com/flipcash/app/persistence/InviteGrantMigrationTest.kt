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
 * Covers the v46 upgrade adding `chat_metadata.can_invite`.
 *
 * Room's auto-migration adds the column at 0. [FlipcashDatabase.Migration45To46] then zeroes every
 * `viewer_state_version`, so the next fetch, which reports the version the row already holds, gets
 * past the write gate and fills the grant.
 */
@RunWith(RobolectricTestRunner::class)
class InviteGrantMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun openV46Shape(): SupportSQLiteDatabase {
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
                "can_edit INTEGER NOT NULL DEFAULT 0, " +
                "can_invite INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(chat_id_hex))"
        )
        return db
    }

    private fun SupportSQLiteDatabase.long(sql: String): Long =
        query(sql).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun `migration 45 to 46 re-arms the viewer state gate and keeps the mute`() {
        val db = openV46Shape()
        db.execSQL(
            "INSERT INTO chat_metadata (chat_id_hex, mute_forever, viewer_state_version, can_edit) VALUES (?,?,?,?)",
            arrayOf<Any?>("aa", 1, 7, 1),
        )
        db.execSQL(
            "INSERT INTO chat_metadata (chat_id_hex, viewer_state_version) VALUES (?,?)",
            arrayOf<Any?>("bb", 3),
        )

        FlipcashDatabase.Migration45To46().onPostMigrate(SupportSQLiteConnection(db))

        assertEquals(0L, db.long("SELECT MAX(viewer_state_version) FROM chat_metadata"))
        assertEquals(0L, db.long("SELECT SUM(can_invite) FROM chat_metadata"))
        // Only the version is reset; state already stored stays until the fetch replaces it.
        assertEquals(1L, db.long("SELECT mute_forever FROM chat_metadata WHERE chat_id_hex = 'aa'"))
        assertEquals(1L, db.long("SELECT can_edit FROM chat_metadata WHERE chat_id_hex = 'aa'"))

        db.close()
    }
}
