package com.flipcash.services.models.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class MediaItemTest {

    private fun rendition(role: MediaItemRendition.Role, available: Boolean = true) =
        MediaItemRendition(
            role = role,
            blobId = BlobId(byteArrayOf(1)),
            blob = if (available) {
                BlobMetadata(
                    mimeType = "image/png",
                    sizeBytes = 1,
                    downloadUrl = "https://cdn/${role.name.lowercase()}",
                    image = null,
                )
            } else null,
        )

    private fun media(vararg renditions: MediaItemRendition) = MediaItem(renditions.toList())

    @Test
    fun `returns the preferred rendition when available`() {
        val display = rendition(MediaItemRendition.Role.DISPLAY)
        val item = media(
            rendition(MediaItemRendition.Role.ORIGINAL),
            display,
            rendition(MediaItemRendition.Role.THUMBNAIL),
        )
        assertSame(display, item.rendition(MediaItemRendition.Role.DISPLAY))
    }

    @Test
    fun `falls back down the ladder to the next highest available`() {
        // Request ORIGINAL, only DISPLAY + THUMBNAIL exist -> DISPLAY.
        val display = rendition(MediaItemRendition.Role.DISPLAY)
        val item = media(display, rendition(MediaItemRendition.Role.THUMBNAIL))
        assertSame(display, item.rendition(MediaItemRendition.Role.ORIGINAL))
    }

    @Test
    fun `falls back to thumbnail when it is the only one available`() {
        val item = media(rendition(MediaItemRendition.Role.THUMBNAIL))
        assertEquals(
            MediaItemRendition.Role.THUMBNAIL,
            item.rendition(MediaItemRendition.Role.ORIGINAL)?.role,
        )
    }

    @Test
    fun `never upgrades above the requested rendition`() {
        // Only ORIGINAL exists; requesting a lower tier must not return it.
        val item = media(rendition(MediaItemRendition.Role.ORIGINAL))
        assertNull(item.rendition(MediaItemRendition.Role.DISPLAY))
        assertNull(item.rendition(MediaItemRendition.Role.THUMBNAIL))
    }

    @Test
    fun `skips a rendition whose blob is not yet available`() {
        // DISPLAY exists but is still uploading (blob null) -> falls through to THUMBNAIL.
        val item = media(
            rendition(MediaItemRendition.Role.DISPLAY, available = false),
            rendition(MediaItemRendition.Role.THUMBNAIL),
        )
        assertEquals(
            MediaItemRendition.Role.THUMBNAIL,
            item.rendition(MediaItemRendition.Role.DISPLAY)?.role,
        )
    }

    @Test
    fun `returns null when nothing at or below the request is available`() {
        assertNull(media().rendition(MediaItemRendition.Role.ORIGINAL))
        val onlyUnready = media(rendition(MediaItemRendition.Role.THUMBNAIL, available = false))
        assertNull(onlyUnready.rendition(MediaItemRendition.Role.ORIGINAL))
    }

    @Test
    fun `unknown role has no fallback chain`() {
        val item = media(rendition(MediaItemRendition.Role.ORIGINAL))
        assertNull(item.rendition(MediaItemRendition.Role.UNKNOWN))
    }

    @Test
    fun `url returns the resolved rendition download url`() {
        val item = media(rendition(MediaItemRendition.Role.THUMBNAIL))
        assertEquals("https://cdn/thumbnail", item.url(MediaItemRendition.Role.ORIGINAL))
        assertNull(media().url(MediaItemRendition.Role.ORIGINAL))
    }
}
