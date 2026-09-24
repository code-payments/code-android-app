package com.flipcash.services.internal.extensions

/**
 * The APPn segments a stripped JPEG may keep: JFIF, the ICC colour profile, and Adobe's colour
 * transform. None carries personal data, and dropping the colour ones visibly shifts a wide-gamut
 * photo.
 */
private val ALLOWED_APP_MARKERS = setOf(0xE0, 0xE2, 0xEE)

private const val MARKER_SOI = 0xD8
private const val MARKER_TEM = 0x01
private const val MARKER_SOS = 0xDA
private const val MARKER_EOI = 0xD9
private const val MARKER_COM = 0xFE

/**
 * This JPEG without the segments that can carry personal data — EXIF (GPS, device serials), XMP,
 * every other APPn outside the allowlist, and free-form comments.
 *
 * Returns the receiver untouched when it carries none, and when it doesn't parse: the decoder
 * rejects a malformed stream on its own, and a half-rewritten one would be worse.
 *
 * Rotation must already be baked into the pixels, since stripping EXIF discards the orientation tag
 * that decoders rely on.
 *
 * Must agree byte-for-byte with iOS's `JPEGMetadata.stripped`
 * (`FlipcashCore/Sources/FlipcashCore/Blob/JPEGMetadata.swift`).
 */
internal fun ByteArray.withoutJpegMetadata(): ByteArray {
    val segments = privacySegments()
    if (segments.isNullOrEmpty()) return this

    val output = ByteArray(size - segments.sumOf { it.last - it.first + 1 })
    var copiedUpTo = 0
    var writeAt = 0
    for (segment in segments) {
        copyInto(output, writeAt, copiedUpTo, segment.first)
        writeAt += segment.first - copiedUpTo
        copiedUpTo = segment.last + 1
    }
    copyInto(output, writeAt, copiedUpTo, size)

    return output
}

/**
 * Walks the marker segments and returns the byte ranges to drop, or null when the stream doesn't
 * parse. Stops at the scan — everything past it is entropy-coded pixel data.
 */
private fun ByteArray.privacySegments(): List<IntRange>? {
    if (size < 2 || byteAt(0) != 0xFF || byteAt(1) != MARKER_SOI) return null

    val segments = mutableListOf<IntRange>()
    var position = 2

    while (position + 1 < size) {
        if (byteAt(position) != 0xFF) return null

        val marker = byteAt(position + 1)

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

        if (marker == MARKER_SOS || marker == MARKER_EOI) return segments

        if (position + 4 > size) return null

        val length = (byteAt(position + 2) shl 8) or byteAt(position + 3)
        if (length < 2 || position + 2 + length > size) return null

        val carriesPersonalData = marker == MARKER_COM ||
            (marker in 0xE0..0xEF && marker !in ALLOWED_APP_MARKERS)

        if (carriesPersonalData) {
            segments.add(position until position + 2 + length)
        }

        position += 2 + length
    }

    return segments
}

private fun ByteArray.byteAt(index: Int): Int = this[index].toInt() and 0xFF
