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

    private fun sized(
        role: MediaItemRendition.Role,
        longestSide: Int,
        available: Boolean = true,
    ) = MediaItemRendition(
        role = role,
        blobId = BlobId(byteArrayOf(1)),
        blob = if (available) {
            BlobMetadata(
                mimeType = "image/png",
                sizeBytes = 1,
                downloadUrl = "https://cdn/${role.name.lowercase()}-$longestSide",
                image = ImageMetadata(width = longestSide, height = longestSide / 2, blurhash = "abc"),
            )
        } else null,
    )

    private fun media(vararg renditions: MediaItemRendition) = MediaItem(renditions.toList())

    // The server's rendition spec: three thumbnails + two display sizes (order intentionally
    // shuffled to prove selection isn't relying on list order).
    private fun sizedMedia() = media(
        sized(MediaItemRendition.Role.THUMBNAIL, 320),
        sized(MediaItemRendition.Role.DISPLAY, 1600),
        sized(MediaItemRendition.Role.THUMBNAIL, 32),
        sized(MediaItemRendition.Role.DISPLAY, 800),
        sized(MediaItemRendition.Role.THUMBNAIL, 160),
        sized(MediaItemRendition.Role.ORIGINAL, 4000),
    )

    @Test
    fun `renditionForSize picks the smallest rendition at least as large as the target`() {
        val item = sizedMedia()
        assertEquals(32, item.renditionForSize(1)?.longestSideForTest())
        assertEquals(160, item.renditionForSize(96)?.longestSideForTest())
        assertEquals(160, item.renditionForSize(160)?.longestSideForTest())
        assertEquals(320, item.renditionForSize(161)?.longestSideForTest())
        assertEquals(800, item.renditionForSize(500)?.longestSideForTest())
    }

    @Test
    fun `renditionForSize falls back to the largest non-original when the target exceeds all`() {
        // 1600 is the biggest derived size; ORIGINAL (4000) is excluded from selection.
        assertEquals(1600, sizedMedia().renditionForSize(9000)?.longestSideForTest())
    }

    @Test
    fun `renditionForSize uses original only when it is the sole sized rendition`() {
        val item = media(sized(MediaItemRendition.Role.ORIGINAL, 4000))
        // No non-original sized renditions -> degrades to the role ladder (DISPLAY cap), which
        // finds nothing at/below DISPLAY, so null rather than serving the huge ORIGINAL.
        assertNull(item.renditionForSize(100))
    }

    @Test
    fun `renditionForSize skips renditions still uploading`() {
        val item = media(
            sized(MediaItemRendition.Role.THUMBNAIL, 160, available = false),
            sized(MediaItemRendition.Role.THUMBNAIL, 320),
        )
        assertEquals(320, item.renditionForSize(96)?.longestSideForTest())
    }

    @Test
    fun `renditionForSize degrades to the role ladder when no dimensions are present`() {
        // Legacy media without ImageMetadata -> DISPLAY-capped role fallback.
        val item = media(
            rendition(MediaItemRendition.Role.DISPLAY),
            rendition(MediaItemRendition.Role.THUMBNAIL),
        )
        assertEquals(MediaItemRendition.Role.DISPLAY, item.renditionForSize(100)?.role)
    }

    @Test
    fun `renditionBelow returns the largest rendition strictly smaller than the target`() {
        val item = sizedMedia()
        assertEquals(160, item.renditionBelow(320)?.longestSideForTest())
        assertEquals(32, item.renditionBelow(160)?.longestSideForTest())
        assertNull(item.renditionBelow(32)) // nothing smaller than the smallest
    }

    @Test
    fun `urlForSize returns the resolved download url`() {
        assertEquals("https://cdn/thumbnail-160", sizedMedia().urlForSize(96))
        assertNull(media().urlForSize(96))
    }

    @Test
    fun `blurhash returns the first available hash`() {
        assertEquals("abc", sizedMedia().blurhash())
        assertNull(media(rendition(MediaItemRendition.Role.THUMBNAIL)).blurhash()) // image == null
    }

    private fun MediaItemRendition.longestSideForTest(): Int? =
        blob?.image?.let { maxOf(it.width, it.height) }

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
