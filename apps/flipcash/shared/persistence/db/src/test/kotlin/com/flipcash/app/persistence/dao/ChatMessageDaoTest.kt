package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.entities.ChatMessageEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the split between the newest stored row and the newest row that still has content. The
 * conversation list previews the latter so a delete falls back to the message before it; mark-read
 * and the receive buzz anchor on the former so a delete can't rewind the read pointer.
 */
@RunWith(RobolectricTestRunner::class)
class ChatMessageDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var dao: ChatMessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chatMessageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun text(messageId: Long, body: String) = ChatMessageEntity(
        chatIdHex = CHAT_HEX,
        messageId = messageId,
        senderIdHex = SENDER_HEX,
        contentJson = listOf(MessageContentSerialized.Text(body)),
        timestampEpochMs = messageId * 1_000,
        unreadSeq = messageId,
    )

    private fun tombstone(messageId: Long) = text(messageId, "gone").copy(
        contentJson = listOf(MessageContentSerialized.Deleted(deletedAt = 1, deletedBy = SENDER_HEX)),
        isDeleted = true,
    )

    @Test
    fun `getLatestVisible skips the tombstone the newest row became`() = runTest {
        dao.upsert(listOf(text(1, "one"), text(2, "two")))
        dao.upsert(tombstone(2))

        assertEquals(2L, dao.getLatest(CHAT_HEX)?.messageId)
        assertEquals(1L, dao.getLatestVisible(CHAT_HEX)?.messageId)
    }

    @Test
    fun `getLatestVisible returns the newest row when nothing is deleted`() = runTest {
        dao.upsert(listOf(text(1, "one"), text(2, "two")))

        assertEquals(2L, dao.getLatestVisible(CHAT_HEX)?.messageId)
    }

    @Test
    fun `getLatestVisible is null once every message is deleted`() = runTest {
        dao.upsert(listOf(text(1, "one"), text(2, "two")))
        dao.upsert(listOf(tombstone(1), tombstone(2)))

        assertEquals(2L, dao.getLatest(CHAT_HEX)?.messageId)
        assertNull(dao.getLatestVisible(CHAT_HEX))
    }

    /**
     * The 31 -> 32 backfill has only the serialized blob to go on, so it matches the discriminator
     * kotlinx.serialization writes for a tombstone. This pins that string to what the converters
     * actually produce — and to what they don't produce for a message that still has content.
     */
    @Test
    fun `the migration backfill flags stored tombstones and leaves text alone`() = runTest {
        // The pre-32 shape: the flag defaulted to 0 for every cached row.
        dao.upsert(listOf(text(1, "one"), tombstone(2).copy(isDeleted = false)))
        assertEquals(2L, dao.getLatestVisible(CHAT_HEX)?.messageId)

        db.openHelper.writableDatabase.execSQL(FlipcashDatabase.Migration31To32.BACKFILL_TOMBSTONES)

        assertEquals(1L, dao.getLatestVisible(CHAT_HEX)?.messageId)
    }

    @Test
    fun `getLatestVisible ignores other chats`() = runTest {
        dao.upsert(listOf(text(1, "one"), text(2, "two")))
        dao.upsert(text(3, "elsewhere").copy(chatIdHex = OTHER_HEX))

        assertEquals(2L, dao.getLatestVisible(CHAT_HEX)?.messageId)
    }

    private companion object {
        const val CHAT_HEX = "aabb"
        const val OTHER_HEX = "ccdd"
        const val SENDER_HEX = "1122"
    }
}
