package com.flipcash.app.persistence.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.converters.MessageContentSerialized
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.entities.MessageStatus
import com.flipcash.app.persistence.entities.PendingMediaEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A queued photo is the one pending row that outlives a refresh and a restart, so the cases here
 * are where the generic pending-row rules (a refresh deletes `SENDING`, the launch sweep fails it)
 * have to step aside for an entry in `pending_media`.
 */
@RunWith(RobolectricTestRunner::class)
class PendingMediaDaoTest {

    private lateinit var db: FlipcashDatabase
    private lateinit var media: PendingMediaDao
    private lateinit var messages: ChatMessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FlipcashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        media = db.pendingMediaDao()
        messages = db.chatMessageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(clientIdHex: String, stored: Boolean = false, createdAt: Long = 1) = PendingMediaEntity(
        clientIdHex = clientIdHex,
        chatIdHex = CHAT_HEX,
        fileName = "$clientIdHex.jpg",
        caption = null,
        replyToMessageId = null,
        storedBlobIdHex = if (stored) "ab".repeat(16) else null,
        sealedForHex = null,
        width = 800,
        height = 600,
        blurhash = null,
        sizeBytes = null,
        createdAt = createdAt,
    )

    private fun pendingRow(clientIdHex: String, messageId: Long) = ChatMessageEntity(
        chatIdHex = CHAT_HEX,
        messageId = messageId,
        senderIdHex = SENDER_HEX,
        contentJson = listOf(MessageContentSerialized.Text("photo")),
        timestampEpochMs = -messageId,
        unreadSeq = 0,
        status = MessageStatus.SENDING,
        pendingClientIdHex = clientIdHex,
    )

    private fun server(messageId: Long) = ChatMessageEntity(
        chatIdHex = CHAT_HEX,
        messageId = messageId,
        senderIdHex = SENDER_HEX,
        contentJson = listOf(MessageContentSerialized.Text("from the server")),
        timestampEpochMs = messageId * 1_000,
        unreadSeq = messageId,
    )

    @Test
    fun `entries come back oldest first`() = runTest {
        media.upsert(listOf(entry("b", createdAt = 2), entry("a", createdAt = 1)))

        assertEquals(listOf("a", "b"), media.getAll().map { it.clientIdHex })
    }

    @Test
    fun `marking an entry stored keeps the rest of it`() = runTest {
        media.upsert(entry("a"))

        media.markStored("a", blobIdHex = "cd".repeat(16), sealedForHex = "ef", sizeBytes = 9, blurhash = "LEHV")

        val stored = media.get("a")!!
        assertEquals("cd".repeat(16), stored.storedBlobIdHex)
        assertEquals("ef", stored.sealedForHex)
        assertEquals(9L, stored.sizeBytes)
        assertEquals("a.jpg", stored.fileName)
    }

    @Test
    fun `a refresh does not delete a queued photo`() = runTest {
        messages.upsert(pendingRow("a", -10))
        messages.upsert(pendingRow("text", -20))
        media.upsert(entry("a"))

        messages.upsertAndClearPending(CHAT_HEX, listOf(server(1)))

        assertEquals(MessageStatus.SENDING, messages.getByClientId(CHAT_HEX, "a")?.status)
        // The ordinary pending row is still cleared.
        assertNull(messages.getByClientId(CHAT_HEX, "text"))
    }

    @Test
    fun `the sweep spares a stored photo and fails an unstored one`() = runTest {
        messages.upsert(pendingRow("stored", -10))
        messages.upsert(pendingRow("unstored", -20))
        messages.upsert(pendingRow("text", -30))
        media.upsert(listOf(entry("stored", stored = true), entry("unstored")))

        messages.failInterruptedSends()

        assertEquals(MessageStatus.SENDING, messages.getByClientId(CHAT_HEX, "stored")?.status)
        assertEquals(MessageStatus.FAILED, messages.getByClientId(CHAT_HEX, "unstored")?.status)
        assertEquals(MessageStatus.FAILED, messages.getByClientId(CHAT_HEX, "text")?.status)
    }

    @Test
    fun `confirming a photo takes the server's content`() = runTest {
        messages.upsert(pendingRow("a", -10))

        messages.confirmPendingMessage(CHAT_HEX, "a", server(7), replaceContent = true)

        val confirmed = messages.getByClientId(CHAT_HEX, "a")!!
        assertEquals(7L, confirmed.messageId)
        assertEquals(listOf(MessageContentSerialized.Text("from the server")), confirmed.contentJson)
    }

    @Test
    fun `confirming keeps the local content unless asked`() = runTest {
        messages.upsert(pendingRow("a", -10))

        messages.confirmPendingMessage(CHAT_HEX, "a", server(7))

        assertEquals(listOf(MessageContentSerialized.Text("photo")), messages.getByClientId(CHAT_HEX, "a")!!.contentJson)
    }

    @Test
    fun `confirming after the stream delivered the message drops the pending row`() = runTest {
        messages.upsert(server(7))
        messages.upsert(pendingRow("a", -10))

        messages.confirmPendingMessage(CHAT_HEX, "a", server(7), replaceContent = true)

        assertNull(messages.getByClientId(CHAT_HEX, "a"))
        assertEquals(7L, messages.getMessage(CHAT_HEX, 7)?.messageId)
    }

    @Test
    fun `confirming keeps the stream's copy when it carries the same client id`() = runTest {
        // A group send whose stream event lands first: the refresh clears the pending row and
        // stores the server's copy under the same client id.
        messages.upsert(server(7).copy(pendingClientIdHex = "a"))

        messages.confirmPendingMessage(CHAT_HEX, "a", server(7))

        assertEquals(7L, messages.getMessage(CHAT_HEX, 7)?.messageId)
    }

    private companion object {
        const val CHAT_HEX = "c0ffee"
        const val SENDER_HEX = "5e1f"
    }
}
