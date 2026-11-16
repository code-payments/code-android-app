package com.flipcash.shared.chat.ui.media

import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MediaItemRendition.Role
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuoteThumbnailRenditionTest {

    private fun sized(role: Role, longestSide: Int, id: Byte) = MediaItemRendition(
        role = role,
        blobId = BlobId(ByteArray(32) { id }),
        blob = BlobMetadata(
            mimeType = "image/jpeg",
            sizeBytes = longestSide.toLong(),
            downloadUrl = "https://cdn/$longestSide",
            image = ImageMetadata(width = longestSide, height = longestSide * 3 / 4, blurhash = "abc"),
        ),
    )

    private fun unhydrated(role: Role, id: Byte) =
        MediaItemRendition(role = role, blobId = BlobId(ByteArray(32) { id }), blob = null)

    private fun quote(item: MediaItem?, sealed: Boolean = false) =
        ChatQuoteSnippet.Photo(caption = null, item = item, sealed = sealed, redacted = false)

    /** What the server returns for a plaintext photo: the original first, then what it derived. */
    private val plaintext = MediaItem(
        listOf(
            sized(Role.ORIGINAL, 4000, 1),
            sized(Role.THUMBNAIL, 32, 2),
            sized(Role.THUMBNAIL, 160, 3),
            sized(Role.THUMBNAIL, 320, 4),
            sized(Role.DISPLAY, 800, 5),
            sized(Role.DISPLAY, 1600, 6),
        ),
    )

    @Test
    fun `a plaintext photo quotes with the smallest thumbnail that covers the square`() {
        // 40dp at 3x and at 4x.
        for (px in listOf(120, 160)) {
            val rendition = quote(plaintext).thumbnailRendition(px)
            assertEquals(Role.THUMBNAIL, rendition?.role, "at $px px")
            assertEquals(160, rendition?.blob?.image?.width, "at $px px")
        }
    }

    @Test
    fun `a sealed photo quotes with its original`() {
        val sealed = MediaItem(listOf(sized(Role.ORIGINAL, 4000, 1)))
        assertEquals(Role.ORIGINAL, quote(sealed, sealed = true).thumbnailRendition(120)?.role)
    }

    @Test
    fun `a photo the server derived nothing for quotes with its original`() {
        val originalOnly = MediaItem(listOf(sized(Role.ORIGINAL, 4000, 1)))
        assertEquals(Role.ORIGINAL, quote(originalOnly).thumbnailRendition(120)?.role)
    }

    @Test
    fun `a photo whose blobs are not hydrated yet still quotes with its first rendition`() {
        val item = MediaItem(listOf(unhydrated(Role.ORIGINAL, 1), unhydrated(Role.THUMBNAIL, 2)))
        assertEquals(item.renditions.first(), quote(item).thumbnailRendition(120))
    }

    @Test
    fun `a quote with no photo item has no thumbnail rendition`() {
        assertNull(quote(item = null).thumbnailRendition(120))
    }
}
