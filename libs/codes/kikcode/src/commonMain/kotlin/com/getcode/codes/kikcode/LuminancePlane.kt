package com.getcode.codes.kikcode

/**
 * The contract between a camera frame's luminance (Y) plane and the native code scanner.
 *
 * `kikCodeScan` does `memcpy(greyscale.data, image, height * width)` — it reads exactly
 * [scannedByteCount] bytes from the front of whatever buffer it is handed, and assumes those bytes
 * are a tightly packed `width * height` greyscale image. Cameras do not always hand us that: rows
 * are commonly padded out to a hardware alignment, so a plane's row stride can exceed its width.
 *
 * Both platforms got this wrong in different directions, which is why the rule lives here:
 *  - Android guarded the unpadding with `pixelStride != -1`, which is vacuously true for a
 *    YUV_420_888 Y plane, so it ran an O(width * height) per-pixel copy on *every* frame — ~1.5 ms
 *    at 1080p — even when the plane was already packed.
 *  - iOS never unpadded at all, and read its stride with `CVPixelBufferGetBytesPerRow` (which
 *    reports a whole-buffer value for planar formats) instead of
 *    `CVPixelBufferGetBytesPerRowOfPlane(_, 0)`. It survives only because the 1080p capture width
 *    happens to be 64-aligned and therefore unpadded.
 *
 * ## Why this shares the decision and not the bytes
 *
 * [unpad] is deliberately *not* part of the iOS-facing surface. Handing a plane across the
 * Kotlin/Native bridge would convert `Data` to `ByteArray`, copying the whole ~2 MB frame — far
 * worse than the copy this is meant to avoid. Callers ask [isTightlyPacked] whether a copy is
 * needed and then move the bytes in their own native code, so the shared piece stays branch-only
 * and allocation-free.
 */
object LuminancePlane {

    /**
     * Whether the plane can be handed to the scanner as-is.
     *
     * When true the first [scannedByteCount] bytes are already the image the scanner expects, so the
     * buffer can be passed through with no copy. When false the caller must repack it — see [unpad]
     * for the reference implementation.
     *
     * A YUV_420_888 Y plane always reports a [pixelStride] of 1 (the format guarantees the Y plane
     * is never interleaved), so in practice only [rowStride] decides this; the pixel-stride term is
     * kept so the rule stays correct if that guarantee ever loosens.
     */
    fun isTightlyPacked(width: Int, rowStride: Int, pixelStride: Int): Boolean =
        rowStride == width && pixelStride == 1

    /** How many bytes the scanner reads for a `width x height` frame. */
    fun scannedByteCount(width: Int, height: Int): Int = width * height

    /**
     * Repacks a padded or interleaved plane into a tightly packed `width * height` buffer.
     *
     * Returns [data] untouched when [isTightlyPacked] already holds, so the common case costs
     * nothing. Note the returned array may be *longer* than [scannedByteCount] — the scanner only
     * reads the front of it.
     *
     * This is the JVM/Android path. iOS repacks in Swift over the raw plane pointer rather than
     * calling this, to keep the frame out of the Kotlin/Native bridge.
     */
    fun unpad(
        data: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): ByteArray {
        if (isTightlyPacked(width, rowStride, pixelStride)) return data

        val cleanData = ByteArray(scannedByteCount(width, height))
        for (y in 0 until height) {
            for (x in 0 until width) {
                cleanData[y * width + x] = data[y * rowStride + x * pixelStride]
            }
        }
        return cleanData
    }
}
