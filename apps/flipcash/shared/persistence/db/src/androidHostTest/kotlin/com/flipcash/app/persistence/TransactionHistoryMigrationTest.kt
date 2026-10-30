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
 * Covers the v29 upgrade dropping the cached transaction history.
 *
 * The feed is a re-fetchable cache ([FlipcashDatabase.Migration28To29]), so the contract is simply
 * "after the upgrade, `messages` is empty and every other table is untouched".
 */
@RunWith(RobolectricTestRunner::class)
class TransactionHistoryMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun openV28Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(callback)
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        db.execSQL(
            "CREATE TABLE messages (idBase58 TEXT NOT NULL, text TEXT NOT NULL, " +
                "amountUsdc INTEGER, amountNative INTEGER, nativeCurrency TEXT, rate REAL, " +
                "state TEXT NOT NULL, timestamp INTEGER NOT NULL, metadata TEXT, " +
                "mintBase58 TEXT DEFAULT 'EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v', " +
                "text_substitutions TEXT, PRIMARY KEY(idBase58))"
        )
        // A neighbouring table, to pin down that the migration only clears the feed.
        db.execSQL(
            "CREATE TABLE blocked_users (user_id_hex TEXT NOT NULL, " +
                "blocked_at_epoch_ms INTEGER NOT NULL, PRIMARY KEY(user_id_hex))"
        )
        return db
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun SupportSQLiteDatabase.insertMessage(id: String, timestamp: Long) = execSQL(
        "INSERT INTO messages (idBase58, text, state, timestamp) VALUES (?,?,?,?)",
        arrayOf<Any?>(id, "sent \$1.00", "COMPLETED", timestamp),
    )

    @Test
    fun `migration 28 to 29 drops the cached transaction history`() {
        val db = openV28Database()
        db.insertMessage("msgA", 1L)
        db.insertMessage("msgB", 2L)
        db.execSQL("INSERT INTO blocked_users VALUES (?,?)", arrayOf<Any?>("u1", 111L))
        assertEquals(2, db.count("messages"))

        FlipcashDatabase.Migration28To29().onPostMigrate(SupportSQLiteConnection(db))

        assertEquals(0, db.count("messages"))
        // Only the feed is cleared — unrelated caches survive the upgrade.
        assertEquals(1, db.count("blocked_users"))

        db.close()
    }

    @Test
    fun `migration 28 to 29 is a no-op on an already-empty feed`() {
        val db = openV28Database()

        FlipcashDatabase.Migration28To29().onPostMigrate(SupportSQLiteConnection(db))

        assertEquals(0, db.count("messages"))

        db.close()
    }
}
