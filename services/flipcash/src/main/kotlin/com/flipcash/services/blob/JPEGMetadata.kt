package com.flipcash.services.blob

/**
 * Strips the JPEG marker segments that can carry personal data — EXIF (GPS, device serials), XMP,
 * and free-form comments — before bytes leave the device.
 *
 * A pure function over [ByteArray], no Android types: this must agree byte-for-byte with iOS's
 * `JPEGMetadata.stripped` (`FlipcashCore/Sources/FlipcashCore/Blob/JPEGMetadata.swift`), and staying
 * platform-free keeps it portable to a shared KMP module later.
 */
object JPEGMetadata {

    /**
     * The APPn segments a stripped JPEG may keep: JFIF, the ICC colour profile, and Adobe's colour
     * transform. None carries personal data, and dropping the colour ones visibly shifts a
     * wide-gamut photo.
     */
    private val ALLOWED_APP_MARKERS = setOf(0xE0, 0xE2, 0xEE)

    private const val MARKER_SOI = 0xD8
    private const val MARKER_TEM = 0x01
    private const val MARKER_SOS = 0xDA
    private const val MARKER_EOI = 0xD9
    private const val MARKER_COM = 0xFE

    /**
     * Returns [jpeg] without the segments that can carry personal data — every APPn outside the
     * allowlist, plus free-form comments.
     *
     * Returns the input untouched when it carries none, and when it doesn't parse: the decoder
     * rejects a malformed stream on its own, and a half-rewritten one would be worse.
     *
     * Rotation must already be baked into the pixels, since stripping EXIF discards the orientation
     * tag that decoders rely on.
     */
    fun stripped(jpeg: ByteArray): ByteArray {
        val segments = privacySegments(jpeg)
        if (segments.isNullOrEmpty()) return jpeg

        val output = ByteArray(jpeg.size - segments.sumOf { it.last - it.first + 1 })
        var copiedUpTo = 0
        var writeAt = 0
        for (segment in segments) {
            val length = segment.first - copiedUpTo
            jpeg.copyInto(output, writeAt, copiedUpTo, segment.first)
            writeAt += length
            copiedUpTo = segment.last + 1
        }
        jpeg.copyInto(output, writeAt, copiedUpTo, jpeg.size)

        return output
    }

    /**
     * Walks the marker segments and returns the byte ranges to drop, or null when the stream
     * doesn't parse. Stops at the scan — everything past it is entropy-coded pixel data.
     */
    private fun privacySegments(bytes: ByteArray): List<IntRange>? {
        if (bytes.size < 2 || (bytes[0].toUByte().toInt()) != 0xFF || (bytes[1].toUByte().toInt()) != MARKER_SOI) {
            return null
        }

        val segments = mutableListOf<IntRange>()
        var position = 2

        while (position + 1 < bytes.size) {
            if (bytes[position].toUByte().toInt() != 0xFF) return null

            val marker = bytes[position + 1].toUByte().toInt()

            // A marker may be padded with any number of 0xFF fill bytes.
            if (marker == 0xFF) {
                position += 1
                continue
            }

            // Standalone markers, the RSTn restart markers included, carry no payload.
            if (marker == MARKER_SOI || marker == MARKER_TEM || marker in 0xD0..0xD7) {
                position += 2
                continue
            }

            if (marker == MARKER_SOS || marker == MARKER_EOI) {
                return segments
            }

            if (position + 4 > bytes.size) return null

            val length = (bytes[position + 2].toUByte().toInt() shl 8) or bytes[position + 3].toUByte().toInt()
            if (length < 2 || position + 2 + length > bytes.size) return null

            val carriesPersonalData = marker == MARKER_COM ||
                (marker in 0xE0..0xEF && marker !in ALLOWED_APP_MARKERS)

            if (carriesPersonalData) {
                segments.add(position until (position + 2 + length))
            }

            position += 2 + length
        }

        return segments
    }
}
