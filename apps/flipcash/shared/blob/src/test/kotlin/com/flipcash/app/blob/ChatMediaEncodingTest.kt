package com.flipcash.app.blob

import com.flipcash.services.models.blob.ImageConstraints
import com.flipcash.services.models.blob.MimeTypeConstraints
import com.flipcash.services.models.blob.UploadPolicy
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

class ChatMediaEncodingTest {

    private fun policy(vararg constraints: MimeTypeConstraints) =
        UploadPolicy(version = "v1", ttl = 1.hours, mimeTypeConstraints = constraints.toList())

    @Test
    fun `plain limits come from the first entry matching image-jpeg`() {
        val limits = ChatMediaLimits.plain(
            policy(
                MimeTypeConstraints("image/png", 1, null),
                MimeTypeConstraints("image/*", 5_000, ImageConstraints(2048, 1024, 3_000_000)),
                MimeTypeConstraints("*/*", 9, null),
            )
        ).getOrThrow()

        assertEquals(ChatMediaLimits(5_000, 2048, 1024, 3_000_000), limits)
    }

    @Test
    fun `plain limits without image bounds are unbounded`() {
        val limits = ChatMediaLimits.plain(policy(MimeTypeConstraints("image/jpeg", 700, null))).getOrThrow()

        assertEquals(ChatMediaLimits(700, 0, 0, 0), limits)
    }

    @Test
    fun `plain limits fail when no entry matches`() {
        val result = ChatMediaLimits.plain(policy(MimeTypeConstraints("video/*", 1, null)))

        assertIs<ChatMediaEncodingException.NoMatchingConstraint>(result.exceptionOrNull())
    }

    @Test
    fun `ladder returns the first quality that fits`() {
        val sizes = mapOf(90 to 900, 80 to 700, 70 to 500, 60 to 300)
        val tried = mutableListOf<Int>()

        val result = JpegLadder.select(cap = 700) { q -> tried += q; ByteArray(sizes.getValue(q)) }

        assertEquals(700, result.getOrThrow().size)
        assertEquals(listOf(90, 80), tried)
    }

    @Test
    fun `ladder is TooLarge when nothing fits`() {
        val result = JpegLadder.select(cap = 100) { ByteArray(200) }

        assertIs<ChatMediaEncodingException.TooLarge>(result.exceptionOrNull())
    }

    @Test
    fun `ladder is EncodingFailed when nothing encodes`() {
        val result = JpegLadder.select(cap = 100) { null }

        assertIs<ChatMediaEncodingException.EncodingFailed>(result.exceptionOrNull())
    }

    @Test
    fun `ladder skips a failed rung and keeps going`() {
        val result = JpegLadder.select(cap = 100) { q -> if (q == 90) null else ByteArray(50) }

        assertEquals(50, result.getOrThrow().size)
    }

    @Test
    fun `ladder measures the size after the metadata strip`() {
        // APP1 (EXIF) segment of 22 bytes on a minimal JPEG; stripped it shrinks under the cap.
        val exif = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0x00, 0x14) + ByteArray(18)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + exif +
            byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02, 0xFF.toByte(), 0xD9.toByte())

        val result = JpegLadder.select(cap = (jpeg.size - exif.size).toLong()) { jpeg }

        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02, 0xFF.toByte(), 0xD9.toByte()),
            result.getOrThrow(),
        )
    }

    @Test
    fun `a non-positive cap is unbounded`() {
        assertEquals(10, JpegLadder.select(cap = 0) { ByteArray(10) }.getOrThrow().size)
    }

    @Test
    fun `non-slash mime type never matches`() {
        assertNull(ChatMediaConstraints.firstMatchIndex(listOf("*/*"), "jpeg"))
    }
}
