package com.flipcash.app.messenger.internal.screens.components

import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ReceiptStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The id a row on screen moves the read pointer to. Rows are newest first, as the reversed
 * transcript holds them, so a stack's front photo is its highest index.
 */
class ReadIdOfTest {

    private val alice = listOf<Byte>(1)

    private val photo = MessageContent.Media(
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
        caption = null,
    )

    private fun bubble(
        id: Long,
        content: MessageContent = photo,
        seconds: Long = id,
        receipt: ReceiptStatus? = null,
        pending: String? = null,
    ) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = content,
        isFromSelf = receipt != null,
        timestamp = Instant.fromEpochSeconds(1_000 + seconds),
        senderId = alice,
        pendingClientIdHex = pending,
        receiptStatus = receipt,
    )

    private fun readIdAt(oldestFirst: List<ChatListItem>, id: Long): Long? {
        val rows = oldestFirst.reversed()
        val index = rows.indexOfFirst { (it as? ChatListItem.ContentBubble)?.messageId == id }
        return readIdOf(index, rows.size) { rows[it] }
    }

    @Test
    fun `a stack's front photo on screen reads the whole stack`() {
        val rows = listOf(bubble(1), bubble(2), bubble(3))
        assertEquals(3L, readIdAt(rows, 1))
    }

    @Test
    fun `a photo that stands alone reads as itself`() {
        val rows = listOf(bubble(1), bubble(2, content = MessageContent.Text("hi")), bubble(3))
        assertEquals(1L, readIdAt(rows, 1))
    }

    @Test
    fun `a stack of the viewer's own photos reads nothing`() {
        val rows = listOf(
            bubble(1, receipt = ReceiptStatus.SENT),
            bubble(2, receipt = ReceiptStatus.SENT),
            bubble(-9, seconds = 3, receipt = ReceiptStatus.SENDING, pending = "ab"),
        )
        assertNull(readIdAt(rows, 1))
    }
}
