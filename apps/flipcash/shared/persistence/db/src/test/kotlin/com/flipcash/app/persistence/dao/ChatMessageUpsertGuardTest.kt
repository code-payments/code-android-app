package com.flipcash.app.persistence.dao

import android.content.Context
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

/**
 * Covers `upsert`'s last-writer-wins guard on `event_sequence`.
 *
 * The same message reaches this DAO from three directions — a fetched page, the event stream,
 * and now a push that carried it — with no ordering between them. The guard is what makes the
 * order they arrive in stop mattering, so its edges are worth pinning: which comparison drops a
 * write, which lets one through, and the sequence-0 passthrough that bypasses it entirely.
 */
@RunWith(RobolectricTestRunner::class)
class ChatMessageUpsertGuardTest {

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

    private fun message(
        messageId: Long = 7,
        body: String,
        eventSequence: Long,
    ) = ChatMessageEntity(
        chatIdHex = CHAT_HEX,
        messageId = messageId,
        senderIdHex = SENDER_HEX,
        contentJson = listOf(MessageContentSerialized.Text(body)),
        timestampEpochMs = messageId * 1_000,
        unreadSeq = messageId,
        eventSequence = eventSequence,
    )

    private suspend fun storedBody(messageId: Long = 7): String? =
        (dao.getMessage(CHAT_HEX, messageId)?.contentJson?.firstOrNull() as? MessageContentSerialized.Text)
            ?.text

    @Test
    fun `a copy older than the stored row is dropped`() = runTest {
        dao.upsert(message(body = "newer", eventSequence = 9))
        dao.upsert(message(body = "older", eventSequence = 4))

        assertEquals("newer", storedBody())
        assertEquals(9L, dao.getEventSequence(CHAT_HEX, 7))
    }

    @Test
    fun `a copy newer than the stored row replaces it`() = runTest {
        dao.upsert(message(body = "older", eventSequence = 4))
        dao.upsert(message(body = "newer", eventSequence = 9))

        assertEquals("newer", storedBody())
        assertEquals(9L, dao.getEventSequence(CHAT_HEX, 7))
    }

    /**
     * The comparison is strict, so a copy at the same sequence writes through rather than being
     * skipped. messaging.v1 tells clients to ignore a copy at or below the held version; the
     * equal case is kept because a confirmed send depends on it — confirmPendingMessage stamps
     * the server's event_sequence onto the optimistic row but leaves its locally written content,
     * and the canonical copy that supersedes it carries that same sequence. A re-delivered push
     * hits the same path and converges because the two copies are the same message, not because
     * the guard stopped the second one.
     */
    @Test
    fun `a copy at the same sequence writes through`() = runTest {
        dao.upsert(message(body = "first", eventSequence = 9))
        dao.upsert(message(body = "second", eventSequence = 9))

        assertEquals("second", storedBody())
    }

    /**
     * Sequence 0 means "unstamped" — a legacy row, or an optimistic row the server has not
     * echoed yet — and the guard lets it past unconditionally. A source that sends 0 for a
     * message the server did stamp therefore overwrites a newer stored copy. The server is not
     * such a source: messaging.v1 constrains event_sequence to >= 1, so this is reachable only
     * from a locally built row.
     */
    @Test
    fun `an unstamped copy bypasses the guard and overwrites a stamped row`() = runTest {
        dao.upsert(message(body = "stamped", eventSequence = 9))
        dao.upsert(message(body = "unstamped", eventSequence = 0))

        assertEquals("unstamped", storedBody())
        assertEquals(0L, dao.getEventSequence(CHAT_HEX, 7))
    }

    @Test
    fun `a stamped copy replaces an unstamped stored row`() = runTest {
        dao.upsert(message(body = "unstamped", eventSequence = 0))
        dao.upsert(message(body = "stamped", eventSequence = 9))

        assertEquals("stamped", storedBody())
        assertEquals(9L, dao.getEventSequence(CHAT_HEX, 7))
    }

    /**
     * The path a push-carried message takes: `applyPushedMessage` hands the DAO a single-element
     * list, so the guard has to hold through the list overload and not only the single-entity one.
     */
    @Test
    fun `the list overload guards each entity on its own`() = runTest {
        dao.upsert(listOf(message(messageId = 1, body = "one newer", eventSequence = 9)))
        dao.upsert(listOf(message(messageId = 2, body = "two older", eventSequence = 4)))

        dao.upsert(
            listOf(
                message(messageId = 1, body = "one stale", eventSequence = 4),
                message(messageId = 2, body = "two fresh", eventSequence = 9),
            )
        )

        assertEquals("one newer", storedBody(messageId = 1))
        assertEquals("two fresh", storedBody(messageId = 2))
    }

    @Test
    fun `the guard is scoped to one chat`() = runTest {
        dao.upsert(message(body = "here newer", eventSequence = 9))
        dao.upsert(message(body = "elsewhere older", eventSequence = 4).copy(chatIdHex = OTHER_HEX))

        assertEquals("here newer", storedBody())
        assertEquals(4L, dao.getEventSequence(OTHER_HEX, 7))
    }

    /**
     * A dropped write must not take the pending id with it. The row a stale copy loses to may
     * still be the optimistic one awaiting confirmation, and losing `pending_client_id_hex`
     * strands it — nothing can match the server's echo back to it afterwards.
     */
    @Test
    fun `a dropped copy leaves the pending id on the stored row`() = runTest {
        dao.upsert(message(body = "newer", eventSequence = 9).copy(
            status = MessageStatus.SENDING,
            pendingClientIdHex = CLIENT_HEX,
        ))

        dao.upsert(message(body = "older", eventSequence = 4))

        assertEquals(CLIENT_HEX, dao.getPendingClientId(CHAT_HEX, 7))
    }

    private companion object {
        const val CHAT_HEX = "aabb"
        const val OTHER_HEX = "ccdd"
        const val SENDER_HEX = "1122"
        const val CLIENT_HEX = "eeff"
    }
}
