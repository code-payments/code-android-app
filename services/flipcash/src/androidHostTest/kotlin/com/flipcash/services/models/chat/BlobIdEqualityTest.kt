package com.flipcash.services.models.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A blob id identifies bytes, so two ids over the same bytes are the same id however they were
 * built. Every read of a persisted [MediaItem] decodes fresh arrays, so an id that compared by
 * array identity would make each read a different [MediaItem] — and `remember(media)` would
 * discard its state, and any in-flight work keyed on it, on every emission.
 */
class BlobIdEqualityTest {

    private val bytes = byteArrayOf(1, 2, 3, 4)

    @Test
    fun `ids over equal bytes are equal`() {
        assertEquals(BlobId(bytes.copyOf()), BlobId(bytes.copyOf()))
    }

    @Test
    fun `equal ids hash alike`() {
        assertEquals(BlobId(bytes.copyOf()).hashCode(), BlobId(bytes.copyOf()).hashCode())
    }

    @Test
    fun `ids over different bytes are not equal`() {
        assertNotEquals(BlobId(bytes), BlobId(byteArrayOf(4, 3, 2, 1)))
    }

    @Test
    fun `ids survive a set, so a batch of blob ids deduplicates`() {
        assertEquals(1, setOf(BlobId(bytes.copyOf()), BlobId(bytes.copyOf())).size)
    }

    @Test
    fun `media items decoded separately from the same bytes are equal`() {
        fun item() = MediaItem(
            renditions = listOf(
                MediaItemRendition(
                    role = MediaItemRendition.Role.THUMBNAIL,
                    blobId = BlobId(bytes.copyOf()),
                    blob = BlobMetadata(
                        mimeType = "image/webp",
                        sizeBytes = 1,
                        downloadUrl = "https://cdn/thumb",
                        image = ImageMetadata(width = 160, height = 160, blurhash = "abc"),
                    ),
                )
            )
        )

        assertEquals(item(), item())
    }
}
