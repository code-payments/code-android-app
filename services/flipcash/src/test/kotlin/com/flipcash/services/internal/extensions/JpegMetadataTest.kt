package com.flipcash.services.internal.extensions

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

/**
 * Ports every case from iOS's `JPEGMetadataTests`
 * (`FlipcashCore/Tests/FlipcashCoreTests/JPEGMetadataTests.swift`) — same inputs, same expectations —
 * so this doubles as a parity check between the two implementations.
 */
class JpegMetadataTest {

    // MARK: - Stripping -

    @Test
    fun `strips the segments that can carry personal data`() {
        val jpeg = JPEG.build(
            JPEG.Segment.App(0xE1, "Exif\u0000\u0000GPS 51.5N".toByteArray()),
            JPEG.Segment.App(0xEF, "vendor-serial-1234".toByteArray()),
            JPEG.Segment.Comment("shot at home"),
        )

        val stripped = jpeg.withoutJpegMetadata()

        assertFalse(stripped.contains("GPS 51.5N"))
        assertFalse(stripped.contains("vendor-serial-1234"))
        assertFalse(stripped.contains("shot at home"))
        assertContentEquals(JPEG.build(), stripped)
    }

    // JFIF, ICC and Adobe carry no personal data, and dropping the colour ones visibly shifts a
    // wide-gamut photo.
    @Test
    fun `keeps the allowed segments verbatim while dropping the rest`() {
        val icc = "ICC_PROFILE\u0000colour-data".toByteArray()
        val jpeg = JPEG.build(
            JPEG.Segment.App(0xE0, "JFIF\u0000".toByteArray()),
            JPEG.Segment.App(0xE1, "Exif\u0000\u0000secret".toByteArray()),
            JPEG.Segment.Comment("drop me"),
            JPEG.Segment.App(0xE2, icc),
            JPEG.Segment.App(0xEE, "Adobe".toByteArray()),
        )

        val stripped = jpeg.withoutJpegMetadata()

        assertContentEquals(
            JPEG.build(
                JPEG.Segment.App(0xE0, "JFIF\u0000".toByteArray()),
                JPEG.Segment.App(0xE2, icc),
                JPEG.Segment.App(0xEE, "Adobe".toByteArray()),
            ),
            stripped,
        )
    }

    // Two APP1s is the common real-world shape: EXIF plus XMP.
    @Test
    fun `strips every APP1, not just the first`() {
        val jpeg = JPEG.build(
            JPEG.Segment.App(0xE1, "Exif\u0000\u0000camera-serial".toByteArray()),
            JPEG.Segment.App(0xE1, "http://ns.adobe.com/xap/1.0/\u0000creator".toByteArray()),
        )

        val stripped = jpeg.withoutJpegMetadata()

        assertFalse(stripped.contains("camera-serial"))
        assertFalse(stripped.contains("creator"))
        assertContentEquals(JPEG.build(), stripped)
    }

    // A marker may be preceded by any number of 0xFF fill bytes; miscounting them would
    // desynchronize the walker and splice the wrong range.
    @Test
    fun `strips a segment introduced by fill bytes`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()) +
            JPEG.segment(JPEG.Segment.Comment("padded secret")) +
            JPEG.scanAndEnd

        val stripped = jpeg.withoutJpegMetadata()

        assertFalse(stripped.contains("padded secret"))
    }

    // RSTn and TEM carry no length, so treating them as length-prefixed would read the following
    // bytes as a size and walk off the segment grid.
    @Test
    fun `walks past standalone markers to reach a later segment`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            byteArrayOf(0xFF.toByte(), 0x01) + // TEM
            byteArrayOf(0xFF.toByte(), 0xD3.toByte()) + // RST3
            JPEG.segment(JPEG.Segment.Comment("after standalones")) +
            JPEG.scanAndEnd

        val stripped = jpeg.withoutJpegMetadata()

        assertFalse(stripped.contains("after standalones"))
    }

    // MARK: - Passthrough -

    @Test
    fun `returns a clean JPEG untouched`() {
        val jpeg = JPEG.build(JPEG.Segment.App(0xE0, "JFIF\u0000".toByteArray()))

        assertContentEquals(jpeg, jpeg.withoutJpegMetadata())
    }

    // The decoder rejects a malformed stream on its own; a half-rewritten one would be worse.
    @Test
    fun `returns a stream whose declared length overruns the data untouched`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            byteArrayOf(0xFF.toByte(), 0xE1.toByte()) +
            // Declares 4KB of payload that isn't there.
            byteArrayOf(0x10, 0x00) +
            "Exif\u0000\u0000truncated".toByteArray()

        assertContentEquals(jpeg, jpeg.withoutJpegMetadata())
    }

    @Test
    fun `returns bytes that are not a JPEG untouched`() {
        val inputs = listOf(
            byteArrayOf(),
            byteArrayOf(0xFF.toByte()),
            ByteArray(512) { 0xAB.toByte() },
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), // PNG signature
        )

        for (input in inputs) {
            assertContentEquals(input, input.withoutJpegMetadata())
        }
    }

    private fun ByteArray.contains(text: String): Boolean {
        val needle = text.toByteArray()
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (start in 0..(size - needle.size)) {
            for (i in needle.indices) {
                if (this[start + i] != needle[i]) continue@outer
            }
            return true
        }
        return false
    }
}

/**
 * Builds minimal JPEG streams: SOI, the given segments, then an empty scan and EOI. Enough for the
 * marker walker without pulling in an image encoder.
 */
private object JPEG {

    sealed interface Segment {
        data class App(val marker: Int, val payload: ByteArray) : Segment
        data class Comment(val text: String) : Segment
    }

    val scanAndEnd: ByteArray = byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02, 0xFF.toByte(), 0xD9.toByte())

    fun build(vararg segments: Segment): ByteArray {
        var data = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        for (segment in segments) {
            data += segment(segment)
        }
        data += scanAndEnd
        return data
    }

    fun segment(segment: Segment): ByteArray = when (segment) {
        is Segment.App -> encode(segment.marker, segment.payload)
        is Segment.Comment -> encode(0xFE, segment.text.toByteArray())
    }

    private fun encode(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        var data = byteArrayOf(0xFF.toByte(), marker.toByte())
        data += byteArrayOf((length shr 8).toByte(), (length and 0xFF).toByte())
        data += payload
        return data
    }
}
