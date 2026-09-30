package com.flipcash.app.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 40 -> 41 is an AutoMigration: it adds `version` and `is_member` to chat_members and creates
 * chat_member_search_tokens and chat_roster_sync. The schemas reach [MigrationTestHelper] as
 * unit-test assets (see this module's build.gradle.kts).
 */
@RunWith(RobolectricTestRunner::class)
class RosterSearchMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FlipcashDatabase::class.java,
    )

    private var room: FlipcashDatabase? = null

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun `migration keeps rows and matches the v41 schema`() {
        helper.createDatabase(DB, 40).use { it.seedV40() }

        val db = helper.runMigrationsAndValidate(DB, 41, true)

        assertEquals(SEEDED_COUNTS, db.counts())
        db.query("SELECT text FROM messages").use { c ->
            c.moveToFirst()
            assertEquals("legacy", c.getString(0))
        }
        db.query("SELECT pointers_json FROM chat_members WHERE user_id_hex = 'b0'").use { c ->
            c.moveToFirst()
            assertEquals("""[]""", c.getString(0))
        }
    }

    @Test
    fun `existing members take the column defaults`() {
        helper.createDatabase(DB, 40).use { it.seedV40() }

        val db = helper.runMigrationsAndValidate(DB, 41, true)

        db.query("SELECT user_id_hex, version, is_member FROM chat_members ORDER BY user_id_hex").use { c ->
            val rows = buildList {
                while (c.moveToNext()) add(Triple(c.getString(0), c.getLong(1), c.getInt(2)))
            }
            assertEquals(listOf(Triple("a0", 0L, 1), Triple("b0", 0L, 1), Triple("c0", 0L, 1)), rows)
        }
    }

    @Test
    fun `new tables exist and are empty`() {
        helper.createDatabase(DB, 40).use { it.seedV40() }

        val db = helper.runMigrationsAndValidate(DB, 41, true)

        assertEquals(0, db.count("chat_member_search_tokens"))
        // No sync row means the first open of each group does a full roster read.
        assertEquals(0, db.count("chat_roster_sync"))
        db.query("PRAGMA index_info(index_chat_member_search_tokens_chat_id_hex_token)").use { c ->
            val columns = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            assertEquals(listOf("chat_id_hex", "token"), columns)
        }
    }

    @Test
    fun `the app's Room builder opens a v40 database without wiping it`() = runBlocking {
        helper.createDatabase(DB, 40).use { it.seedV40() }

        // Same builder as FlipcashDatabase.init. It falls back to a destructive migration,
        // so a broken AutoMigration would open an empty database instead of throwing.
        val opened = Room.databaseBuilder(context, FlipcashDatabase::class.java, DB)
            .addMigrations(FlipcashDatabase.MIGRATION_25_26)
            .fallbackToDestructiveMigration()
            .build()
            .also { room = it }

        val sqlite = opened.openHelper.writableDatabase
        assertEquals(41, sqlite.version)
        assertEquals(SEEDED_COUNTS, sqlite.counts())

        val search = opened.chatMemberSearchDao()
        assertEquals(3, search.countMembers(CHAT))
        assertEquals(3, opened.chatMemberDao().getMembersForChat(CHAT).size)

        // Existing members become searchable once the roster read indexes them.
        search.replaceTokens(CHAT, "b0", listOf("bob"))
        val hits = search.searchByTokenRange(CHAT, selfIdHex = "a0", lower = "bo", upper = "bo􏿿", recentWindow = 50)
        assertEquals(listOf("b0"), hits.map { it.userIdHex })
        assertTrue(hits.single().lastSpokeEpochMs != null, "b0 sent a held message")
    }

    private fun SupportSQLiteDatabase.seedV40() {
        execSQL(
            "INSERT INTO chat_metadata (chat_id_hex, chat_type, last_activity_epoch_ms, last_message_id, " +
                "title, member_count, roster_version) VALUES ('$CHAT', 'GROUP', 1000, 2, 'Group', 3, 9)"
        )
        execSQL(
            "INSERT INTO chat_metadata (chat_id_hex, chat_type, last_activity_epoch_ms) " +
                "VALUES ('$OTHER_CHAT', 'CONTACT_DM', 500)"
        )
        execSQL("INSERT INTO chat_members (chat_id_hex, user_id_hex, pointers_json) VALUES ('$CHAT', 'a0', NULL)")
        execSQL("""INSERT INTO chat_members (chat_id_hex, user_id_hex, pointers_json) VALUES ('$CHAT', 'b0', '[]')""")
        execSQL("INSERT INTO chat_members (chat_id_hex, user_id_hex, pointers_json) VALUES ('$CHAT', 'c0', NULL)")
        execSQL(
            "INSERT INTO chat_messages (chat_id_hex, message_id, sender_id_hex, content_json, " +
                "timestamp_epoch_ms, unread_seq) VALUES ('$CHAT', 1, 'b0', NULL, 900, 1)"
        )
        execSQL(
            "INSERT INTO chat_messages (chat_id_hex, message_id, sender_id_hex, content_json, " +
                "timestamp_epoch_ms, unread_seq) VALUES ('$CHAT', 2, 'a0', NULL, 1000, 2)"
        )
        execSQL(
            "INSERT INTO messages (idBase58, text, state, timestamp) VALUES ('m1', 'legacy', 'SENT', 100)"
        )
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun SupportSQLiteDatabase.counts(): Map<String, Int> =
        SEEDED_COUNTS.keys.associateWith { count(it) }

    private companion object {
        const val DB = "roster-search-migration-test"
        const val CHAT = "c1"
        const val OTHER_CHAT = "c2"
        val SEEDED_COUNTS = mapOf("chat_metadata" to 2, "chat_members" to 3, "chat_messages" to 2, "messages" to 1)
    }
}
