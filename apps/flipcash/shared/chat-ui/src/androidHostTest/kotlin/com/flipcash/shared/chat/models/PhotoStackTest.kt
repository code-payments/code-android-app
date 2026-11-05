package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.PhotoStack.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A row learns its card stack from the rows around it. The transcript is newest first, so a row's
 * older neighbour is the next index, and the stack's front photo is the oldest message.
 */
class PhotoStackTest {

    private val alice = listOf<Byte>(1)
    private val bob = listOf<Byte>(2)

    private fun photo(caption: String? = null) = MessageContent.Media(
        items = listOf(
            MediaItem(
                listOf(
                    MediaItemRendition(
                        role = MediaItemRendition.Role.ORIGINAL,
                        blobId = BlobId(ByteArray(32) { 1 }),
                        blob = BlobMetadata(
                            mimeType = "image/jpeg",
                            sizeBytes = 10,
                            downloadUrl = "https://cdn/blob",
                            image = ImageMetadata(width = 800, height = 600, blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"),
                        ),
                    ),
                ),
            ),
        ),
        caption = caption?.let { MessageContent.Text(it) },
    )

    private fun bubble(
        id: Long,
        content: MessageContent = photo(),
        sender: List<Byte> = alice,
        pending: String? = null,
        seconds: Long = id,
        receipt: ReceiptStatus? = null,
    ) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = content,
        isFromSelf = receipt != null,
        timestamp = Instant.fromEpochSeconds(1_000 + seconds),
        senderId = sender,
        pendingClientIdHex = pending,
        receiptStatus = receipt,
    )

    /** The stack of the row for [id] in [oldestFirst], resolved through the newest-first peek. */
    private fun stackOf(oldestFirst: List<ChatListItem>, id: Long): PhotoStack? {
        val rows = oldestFirst.reversed()
        val index = rows.indexOfFirst { (it as? ChatListItem.ContentBubble)?.messageId == id }
        return photoStackOf(index, rows[index], rows.size) { rows[it] }
    }

    @Test
    fun `three photos sent together are a front, a middle and a last row of one set`() {
        val rows = listOf(bubble(1), bubble(2), bubble(3))

        assertEquals(Role.Front, stackOf(rows, 1)?.role)
        assertEquals(Role.Middle, stackOf(rows, 2)?.role)
        assertEquals(Role.Last, stackOf(rows, 3)?.role)
        // Every row sees the whole set, oldest first, so the front photo is the first.
        listOf(1L, 2L, 3L).forEach { id ->
            assertEquals(listOf(1L, 2L, 3L), stackOf(rows, id)?.members?.map { it.messageId })
        }
        assertEquals(3, stackOf(rows, 2)?.count)
    }

    @Test
    fun `two photos are a front and a last row`() {
        val rows = listOf(bubble(1), bubble(2))

        assertEquals(Role.Front, stackOf(rows, 1)?.role)
        assertEquals(Role.Last, stackOf(rows, 2)?.role)
    }

    @Test
    fun `a photo on its own is not a stack`() {
        assertNull(stackOf(listOf(bubble(1)), 1))
        assertNull(stackOf(listOf(bubble(1), bubble(5)), 5))
    }

    @Test
    fun `a caption ends the set and its owner is the last row`() {
        val rows = listOf(bubble(1), bubble(2, photo("sunset")), bubble(3))

        assertEquals(listOf(1L, 2L), stackOf(rows, 1)?.members?.map { it.messageId })
        assertEquals(Role.Last, stackOf(rows, 2)?.role)
        assertNull(stackOf(rows, 3))
    }

    @Test
    fun `a message from someone else in between splits the set`() {
        val rows = listOf(bubble(1), bubble(2, sender = bob), bubble(3))

        assertNull(stackOf(rows, 1))
        assertNull(stackOf(rows, 2))
        assertNull(stackOf(rows, 3))
    }

    @Test
    fun `a send still going up stacks, though its placeholder ids run backwards`() {
        // Placeholder ids are the negated creation time, so they fall as the send goes on.
        val rows = listOf(
            bubble(-5_000, pending = "aa", receipt = ReceiptStatus.SENDING, seconds = 10),
            bubble(-5_001, pending = "bb", receipt = ReceiptStatus.SENDING, seconds = 10),
            bubble(-5_002, pending = "cc", receipt = ReceiptStatus.SENDING, seconds = 10),
        )

        assertEquals(listOf(-5_000L, -5_001L, -5_002L), stackOf(rows, -5_001)?.members?.map { it.messageId })
    }

    @Test
    fun `a send half posted stays one stack`() {
        val rows = listOf(
            bubble(7, pending = "aa", receipt = ReceiptStatus.SENT, seconds = 10),
            bubble(-5_001, pending = "bb", receipt = ReceiptStatus.SENDING, seconds = 10),
            bubble(-5_002, pending = "cc", receipt = ReceiptStatus.SENDING, seconds = 10),
        )

        assertEquals(listOf(7L, -5_001L, -5_002L), stackOf(rows, 7)?.members?.map { it.messageId })
    }

    @Test
    fun `a failed photo leaves the stack and splits the send`() {
        val rows = listOf(
            bubble(1, pending = "aa", receipt = ReceiptStatus.SENT),
            bubble(-5_001, pending = "bb", receipt = ReceiptStatus.FAILED, seconds = 1),
            bubble(2, pending = "cc", receipt = ReceiptStatus.SENT),
        )

        assertNull(stackOf(rows, 1))
        assertNull(stackOf(rows, -5_001))
        assertNull(stackOf(rows, 2))
    }

    @Test
    fun `photos this device sent stack once confirmed, though they keep their client ids`() {
        val rows = listOf(
            bubble(1, pending = "aa", receipt = ReceiptStatus.SENT),
            bubble(2, pending = "bb", receipt = ReceiptStatus.READ),
        )

        assertEquals(listOf(1L, 2L), stackOf(rows, 1)?.members?.map { it.messageId })
    }

    @Test
    fun `a failed row does not stack`() {
        val rows = listOf(
            bubble(1, pending = "aa", receipt = ReceiptStatus.SENT),
            bubble(2, pending = "bb", receipt = ReceiptStatus.FAILED),
        )

        assertNull(stackOf(rows, 1))
    }

    @Test
    fun `a text message between photos splits them`() {
        val rows = listOf(bubble(1), bubble(2, MessageContent.Text("hi")), bubble(3))

        assertNull(stackOf(rows, 1))
        assertNull(stackOf(rows, 3))
    }

    @Test
    fun `photos too far apart in time do not stack`() {
        val rows = listOf(bubble(1, seconds = 0), bubble(2, seconds = 10))

        assertNull(stackOf(rows, 1))
    }

    @Test
    fun `a photo reply starts a new set`() {
        val reply = MessageContent.Reply(repliedMessageId = 99, content = listOf(photo()))
        val rows = listOf(bubble(1), bubble(2, reply), bubble(3))

        assertNull(stackOf(rows, 1))
        assertEquals(Role.Front, stackOf(rows, 2)?.role)
        assertEquals(listOf(2L, 3L), stackOf(rows, 2)?.members?.map { it.messageId })
    }

    @Test
    fun `rows past the edges of the loaded list do not throw`() {
        val rows = listOf<ChatListItem>(bubble(1))
        assertNull(photoStackOf(0, rows[0], 1) { error("no neighbour to peek at") })
    }
}
