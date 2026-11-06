package com.flipcash.shared.chat.ui.media

import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.PhotoMessageContext
import com.flipcash.shared.chat.models.PhotoStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Save All, as iOS offers it: on a stack's front row only, saving every photo it can, oldest first. */
class SavableStackTest {

    private val chatId = ChatId(ByteArray(16) { it.toByte() })

    /** A photo whose blob is [blob]; an empty one stands for a send not on the server yet. */
    private fun bubble(id: Long, blob: ByteArray = ByteArray(32) { id.toByte() }, redacted: Boolean = false) =
        ChatListItem.ContentBubble(
            messageId = id,
            contentIndex = 0,
            content = MessageContent.Media(
                items = listOf(
                    MediaItem(
                        listOf(
                            MediaItemRendition(
                                role = MediaItemRendition.Role.ORIGINAL,
                                blobId = BlobId(blob),
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
            ),
            isFromSelf = false,
            timestamp = Instant.fromEpochSeconds(1_000 + id),
            photo = PhotoMessageContext(chatId, sealed = false, redacted = redacted, previewing = false),
        )

    private val members = listOf(bubble(1), bubble(2), bubble(3))

    @Test
    fun `a stack's front saves every photo in it, oldest first`() {
        val save = savableStack(PhotoStack(PhotoStack.Role.Front, members))!!

        assertEquals(3, save.total)
        assertEquals(members.map { savablePhoto(it) }, save.photos)
    }

    @Test
    fun `only the front row offers Save All`() {
        assertNull(savableStack(PhotoStack(PhotoStack.Role.Middle, members)))
        assertNull(savableStack(PhotoStack(PhotoStack.Role.Last, members)))
        assertNull(savableStack(null))
    }

    @Test
    fun `a photo that can't be saved still counts against the total`() {
        val stack = PhotoStack(PhotoStack.Role.Front, listOf(bubble(1), bubble(2, blob = ByteArray(0)), bubble(3)))

        val save = savableStack(stack)!!

        assertEquals(3, save.total)
        assertEquals(2, save.photos.size)
    }

    @Test
    fun `a redacted photo is left out of the total, as Save Photo leaves it out`() {
        val stack = PhotoStack(PhotoStack.Role.Front, listOf(bubble(1), bubble(2, redacted = true), bubble(3)))

        val save = savableStack(stack)!!

        assertEquals(2, save.total)
        assertEquals(2, save.photos.size)
    }
}
