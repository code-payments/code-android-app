package com.getcode.codes.kikcode

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the packing rule every analyzed camera frame goes through, on both platforms.
 *
 * Android's guard used to read `pixelStride != -1`, which is vacuously true for a YUV_420_888 Y
 * plane (whose pixel stride is always 1), so the `||` short-circuited to always-true and the
 * per-pixel copy ran on every frame even for tightly packed buffers. iOS had the opposite bug: it
 * never unpadded at all. These tests run on both targets so the rule cannot drift again.
 */
class LuminancePlaneTest {

    /** Android's pre-fix guard, kept verbatim so we can assert the two agree on output. */
    private fun legacyUnpad(
        data: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): ByteArray {
        if (width != rowStride || pixelStride != -1) {
            val cleanData = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    cleanData[y * width + x] = data[y * rowStride + x * pixelStride]
                }
            }
            return cleanData
        }
        return data
    }

    private fun plane(height: Int, rowStride: Int): ByteArray =
        ByteArray(rowStride * height) { (it % 251).toByte() }

    @Test
    fun `a plane is tightly packed only when the row stride matches the width`() {
        assertTrue(LuminancePlane.isTightlyPacked(width = 1920, rowStride = 1920, pixelStride = 1))
        // 64-byte row alignment, the usual source of padding on both platforms
        assertFalse(LuminancePlane.isTightlyPacked(width = 1440, rowStride = 1472, pixelStride = 1))
        assertFalse(LuminancePlane.isTightlyPacked(width = 1000, rowStride = 1024, pixelStride = 1))
        // interleaved planes are never packed, even when the arithmetic happens to line up
        assertFalse(LuminancePlane.isTightlyPacked(width = 64, rowStride = 64, pixelStride = 2))
    }

    @Test
    fun `the scanner reads exactly width times height bytes`() {
        assertEquals(1920 * 1080, LuminancePlane.scannedByteCount(1920, 1080))
    }

    @Test
    fun `tightly packed plane takes the fast path and avoids a copy`() {
        val width = 640
        val height = 480
        val data = plane(height, rowStride = width)

        val result = LuminancePlane.unpad(data, width, height, rowStride = width, pixelStride = 1)

        assertSame(data, result, "tightly packed plane should be returned without copying")
    }

    @Test
    fun `padded plane still strips row padding`() {
        val width = 640
        val height = 480
        val rowStride = 768 // 128 bytes of row padding
        val data = plane(height, rowStride)

        val result = LuminancePlane.unpad(data, width, height, rowStride, pixelStride = 1)

        assertEquals(width * height, result.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(data[y * rowStride + x], result[y * width + x], "mismatch at ($x,$y)")
            }
        }
    }

    @Test
    fun `interleaved plane still honours pixel stride`() {
        val width = 32
        val height = 16
        val pixelStride = 2
        val rowStride = width * pixelStride
        val data = plane(height, rowStride)

        val result = LuminancePlane.unpad(data, width, height, rowStride, pixelStride)

        assertEquals(width * height, result.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(data[y * rowStride + x * pixelStride], result[y * width + x])
            }
        }
    }

    /**
     * The Android fix is a pure speedup: for every plane geometry the camera can hand us, the bytes
     * the scanner sees must be byte-identical to what the old code produced.
     */
    @Test
    fun `output is byte-identical to the pre-fix implementation`() {
        val geometries = listOf(
            Triple(640, 480, 640),
            Triple(640, 480, 768),
            Triple(1280, 720, 1280),
            Triple(1280, 720, 1408),
            Triple(1920, 1080, 1920),
            Triple(1920, 1080, 2048),
        )

        for ((width, height, rowStride) in geometries) {
            val data = plane(height, rowStride)
            val fixed = LuminancePlane.unpad(data, width, height, rowStride, pixelStride = 1)
            val legacy = legacyUnpad(data, width, height, rowStride, pixelStride = 1)

            // The fast path hands back the backing array, which is longer than width*height when
            // the buffer is over-allocated; the scanner only reads the first width*height.
            val scanned = LuminancePlane.scannedByteCount(width, height)
            assertTrue(fixed.size >= scanned, "${width}x$height/$rowStride too small")
            assertContentEquals(
                legacy.copyOf(scanned),
                fixed.copyOf(scanned),
                "content drift at ${width}x$height rowStride=$rowStride",
            )
        }
    }
}
