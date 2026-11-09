package com.flipcash.app.persistence

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the v53 upgrade that adds the launch-scan indexes as partial indexes. See
 * [ChatPartialIndexes].
 *
 * Opening an upgraded database through Room is the check that its schema validation accepts an
 * index whose `WHERE` the entity can't declare: if it compared the `WHERE`, the open would throw.
 */
@RunWith(RobolectricTestRunner::class)
class ChatPartialIndexMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.cacheDir, "partial-index-migration.db")

    @After
    fun tearDown() {
        context.deleteDatabase(file.path)
    }

    /** A v52 database built from the exported schema, as an install upgrading to v53 has it. */
    private fun createV52(): SupportSQLiteDatabase {
        val schema = JSONObject(
            File("schemas/com.flipcash.app.persistence.FlipcashDatabase/52.json").readText()
        ).getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(52) {
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

    private fun openV53(): FlipcashDatabase =
        Room.databaseBuilder(context, FlipcashDatabase::class.java, file.path)
            .addCallback(ChatPartialIndexes.Callback)
            .allowMainThreadQueries()
            .build()

    private fun SupportSQLiteDatabase.indexSql(): Map<String, String> =
        query("SELECT name, sql FROM sqlite_master WHERE type = 'index' AND tbl_name = 'chat_messages' AND sql IS NOT NULL")
            .use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) } }

    private fun SupportSQLiteDatabase.plan(sql: String): String =
        query("EXPLAIN QUERY PLAN $sql").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("detail"))) }.joinToString(" | ")
        }

    private fun assertPartial(db: SupportSQLiteDatabase) {
        val sql = db.indexSql()
        for (name in ChatPartialIndexes.definitions.keys) {
            val create = sql[name]
            assertTrue(create != null && " WHERE " in create, "$name is $create")
        }
        // The transcript's index is untouched.
        assertTrue("index_chat_messages_chat_id_hex_timestamp_epoch_ms" in sql)
    }

    @Test
    fun `migration 52 to 53 keeps the rows and makes the launch-scan indexes partial`() {
        createV52().apply {
            execSQL(
                "INSERT INTO chat_messages (chat_id_hex, message_id, timestamp_epoch_ms, unread_seq, status, pending_client_id_hex, encryption_state) VALUES " +
                    "('aa', 1, 1, 1, 'SENT', NULL, NULL), " +
                    "('aa', 2, 2, 2, 'SENDING', 'c1', NULL), " +
                    "('bb', 3, 3, 3, 'SENT', 'c2', 'KEY_PENDING')"
            )
            close()
        }

        val db = openV53()
        val sql = db.openHelper.writableDatabase

        assertPartial(sql)
        assertEquals(3L, sql.query("SELECT COUNT(*) FROM chat_messages").use { it.moveToFirst(); it.getLong(0) })
        runBlocking {
            assertEquals(listOf("bb"), db.chatMessageDao().chatsWithKeyPending())
            assertEquals(false, db.chatMessageDao().hasDuplicateClientIds())
            db.chatMessageDao().failInterruptedSends()
        }
        assertEquals(
            "FAILED",
            sql.query("SELECT status FROM chat_messages WHERE message_id = 2").use { it.moveToFirst(); it.getString(0) },
        )
        db.close()
    }

    @Test
    fun `a fresh database gets the partial indexes, and the launch scans use them`() {
        val db = openV53()
        val sql = db.openHelper.writableDatabase

        assertPartial(sql)
        // The conditions the three scans in ChatMessageDao filter on.
        assertTrue(
            ChatPartialIndexes.SENDING in sql.plan("SELECT 1 FROM chat_messages WHERE status = 'SENDING'"),
        )
        assertTrue(
            ChatPartialIndexes.KEY_PENDING in
                sql.plan("SELECT DISTINCT chat_id_hex FROM chat_messages WHERE encryption_state = 'KEY_PENDING'"),
        )
        assertTrue(
            ChatPartialIndexes.CLIENT_ID in sql.plan(
                "SELECT 1 FROM chat_messages WHERE pending_client_id_hex IS NOT NULL " +
                    "GROUP BY chat_id_hex, pending_client_id_hex HAVING COUNT(*) > 1"
            ),
        )
        db.close()
    }

    @Test
    fun `an index rebuilt without its WHERE is made partial again on the next open`() {
        openV53().apply { openHelper.writableDatabase; close() }
        val raw = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(file.path).callback(
                object : SupportSQLiteOpenHelper.Callback(53) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build()
        ).writableDatabase
        // What a later auto-migration that rebuilds chat_messages would leave.
        raw.execSQL("DROP INDEX `${ChatPartialIndexes.SENDING}`")
        raw.execSQL("CREATE INDEX `${ChatPartialIndexes.SENDING}` ON `chat_messages` (`status`)")
        raw.close()

        val db = openV53()
        assertPartial(db.openHelper.writableDatabase)
        db.close()
    }
}
