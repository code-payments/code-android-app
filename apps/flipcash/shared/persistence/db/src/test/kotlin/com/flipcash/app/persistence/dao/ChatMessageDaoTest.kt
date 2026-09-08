package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.entities.MessageStatus
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

    private fun pending(body: String) = ChatMessageEntity(
        chatIdHex = CHAT_HEX,
        messageId = -1,
        senderIdHex = SENDER_HEX,
        contentJson = listOf(MessageContentSerialized.Text(body)),
        timestampEpochMs = 1,
        unreadSeq = 0,
        status = MessageStatus.SENDING,
        pendingClientIdHex = CLIENT_HEX,
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

    /**
     * The optimistic row is written with no event sequence, because the client has none to write:
     * the server stamps it. Confirming has to carry the echo's stamp onto the row, or the message
     * stays at sequence 0 until something else refetches the chat — and everything keyed on the
     * stamp (edit, delete, last-writer-wins) treats it as unacknowledged in the meantime.
     */
    @Test
    fun `confirming a pending message carries the server's event sequence`() = runTest {
        dao.upsert(pending("hello"))

        dao.confirmPendingMessage(
            CHAT_HEX,
            CLIENT_HEX,
            text(7, "hello").copy(eventSequence = 42, unreadSeq = 3),
        )

        val stored = dao.getMessage(CHAT_HEX, 7)!!
        assertEquals(42L, stored.eventSequence)
        assertEquals(MessageStatus.SENT, stored.status)
        assertEquals(3L, stored.unreadSeq)
    }

    /**
     * Two messages can share a millisecond — a burst send, or a server batch stamped from one clock
     * read. `timestamp_epoch_ms DESC` alone leaves their order to SQLite, so a paged read that
     * re-queries per page can hand back the same row twice or skip one. The tie-breaker makes the
     * order total.
     */
    @Test
    fun `messages sharing a timestamp order by message id, newest first`() = runTest {
        val sameMs = 5_000L
        dao.upsert(
            listOf(
                text(1, "first").copy(timestampEpochMs = sameMs),
                text(2, "second").copy(timestampEpochMs = sameMs),
                text(3, "third").copy(timestampEpochMs = sameMs),
            )
        )

        val page = dao.observeMessagesPaged(CHAT_HEX).load(
            PagingSource.LoadParams.Refresh(null, 10, false)
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf(3L, 2L, 1L), page.data.map { it.messageId })
    }

    /**
     * How far back a message sits from the newest, which is what bounds the jump walk. Counts
     * strictly newer rows, so the newest message is at distance 0.
     */
    @Test
    fun `countNewerThan measures the distance back to a message`() = runTest {
        dao.upsert((1L..5L).map { text(it, "m$it") })

        assertEquals(0, dao.countNewerThan(CHAT_HEX, 5 * 1_000))
        assertEquals(2, dao.countNewerThan(CHAT_HEX, 3 * 1_000))
        assertEquals(4, dao.countNewerThan(CHAT_HEX, 1 * 1_000))
    }

    @Test
    fun `countNewerThan ignores other chats`() = runTest {
        dao.upsert((1L..5L).map { text(it, "m$it") })
        dao.upsert(text(9, "elsewhere").copy(chatIdHex = OTHER_HEX))

        assertEquals(2, dao.countNewerThan(CHAT_HEX, 3 * 1_000))
    }

    private companion object {
        const val CHAT_HEX = "aabb"
        const val OTHER_HEX = "ccdd"
        const val SENDER_HEX = "1122"
        const val CLIENT_HEX = "eeff"
    }
}
