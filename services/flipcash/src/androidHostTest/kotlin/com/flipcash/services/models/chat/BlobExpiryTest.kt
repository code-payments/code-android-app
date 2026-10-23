package com.flipcash.services.models.chat

import com.getcode.utils.base58
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * `DownloadUrl.expires_at` is what tells a persisted [MediaItem] that its URL needs re-minting
 * before it can be fetched again — these cover the model's half of that.
 */
class BlobExpiryTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun blob(url: String, expiresAt: Instant?) = BlobMetadata(
        mimeType = "image/png",
        sizeBytes = 1,
        downloadUrl = url,
        image = ImageMetadata(width = 160, height = 160, blurhash = "abc"),
        expiresAtMillis = expiresAt?.toEpochMilliseconds(),
    )

    private fun rendition(id: Byte, longestSide: Int, expiresAt: Instant?) = MediaItemRendition(
        role = MediaItemRendition.Role.THUMBNAIL,
        blobId = BlobId(byteArrayOf(id)),
        blob = BlobMetadata(
            mimeType = "image/png",
            sizeBytes = 1,
            downloadUrl = "https://cdn/$id",
            image = ImageMetadata(width = longestSide, height = longestSide, blurhash = "abc"),
            expiresAtMillis = expiresAt?.toEpochMilliseconds(),
        ),
    )

    @Test
    fun `metadata without an expiry is never reported expired`() {
        assertFalse(blob("https://cdn/a", expiresAt = null).isDownloadUrlExpired(now))
    }

    @Test
    fun `an expiry comfortably ahead of now is not expired`() {
        assertFalse(blob("https://cdn/a", now + 5.minutes).isDownloadUrlExpired(now))
    }

    @Test
    fun `an expiry in the past is expired`() {
        assertTrue(blob("https://cdn/a", now - 1.seconds).isDownloadUrlExpired(now))
    }

    @Test
    fun `an expiry inside the margin is expired, so a URL cannot die mid-flight`() {
        val insideMargin = now + BlobMetadata.EXPIRY_MARGIN - 1.seconds
        assertTrue(blob("https://cdn/a", insideMargin).isDownloadUrlExpired(now))

        val outsideMargin = now + BlobMetadata.EXPIRY_MARGIN + 1.seconds
        assertFalse(blob("https://cdn/a", outsideMargin).isDownloadUrlExpired(now))
    }

    @Test
    fun `expiresAt round-trips the stored millis`() {
        assertEquals(now, blob("https://cdn/a", now).expiresAt)
        assertNull(blob("https://cdn/a", null).expiresAt)
    }

    @Test
    fun `cacheKey is the base58 blob id, so it survives a re-mint`() {
        val id = BlobId(byteArrayOf(7, 8, 9))
        val before = MediaItemRendition(MediaItemRendition.Role.THUMBNAIL, id, blob("https://cdn/a", now))
        val after = before.copy(blob = blob("https://cdn/b", now + 1.minutes))
        assertEquals(id.bytes.base58, before.cacheKey)
        assertEquals(before.cacheKey, after.cacheKey)
    }

    @Test
    fun `expiredRenditionForSize only reports the rendition the size actually resolves to`() {
        // The 160 the small surfaces use is still good; the 320 the large ones use has expired.
        val item = MediaItem(
            listOf(
                rendition(id = 1, longestSide = 160, expiresAt = now + 5.minutes),
                rendition(id = 2, longestSide = 320, expiresAt = now - 5.minutes),
            )
        )
        assertNull(item.expiredRenditionForSize(96, now))
        assertEquals(byteArrayOf(2).base58, item.expiredRenditionForSize(300, now)?.cacheKey)
    }

    @Test
    fun `withRefreshedBlobs swaps only the renditions it was given`() {
        val item = MediaItem(
            listOf(
                rendition(id = 1, longestSide = 160, expiresAt = now - 5.minutes),
                rendition(id = 2, longestSide = 320, expiresAt = now - 5.minutes),
            )
        )
        val fresh = blob("https://cdn/2-fresh", now + 10.minutes)

        val refreshed = item.withRefreshedBlobs(mapOf(byteArrayOf(2).base58 to fresh))

        assertEquals("https://cdn/1", refreshed.urlForSize(96))
        assertEquals("https://cdn/2-fresh", refreshed.urlForSize(300))
        assertFalse(refreshed.renditionForSize(300)!!.isDownloadUrlExpired(now))
    }

    @Test
    fun `withRefreshedBlobs on an empty map is the same instance`() {
        val item = MediaItem(listOf(rendition(id = 1, longestSide = 160, expiresAt = null)))
        assertSame(item, item.withRefreshedBlobs(emptyMap()))
    }
}
