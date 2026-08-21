package com.getcode.util

import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the Y-plane unpadding used by every analyzed camera frame.
 *
 * The guard used to read `pixelStride != -1`, which is vacuously true for a YUV_420_888 Y plane
 * (whose pixel stride is always 1), so the `||` short-circuited to always-true and the per-pixel
 * copy ran on every frame even for tightly packed buffers.
 */
class LuminancePlaneTest {

    /** The pre-fix guard, kept verbatim so we can assert the two agree on output. */
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

    private fun plane(width: Int, height: Int, rowStride: Int): ByteArray =
        ByteArray(rowStride * height) { (it % 251).toByte() }

    @Test
    fun `tightly packed plane takes the fast path and avoids a copy`() {
        val width = 640
        val height = 480
        val data = plane(width, height, rowStride = width)

        val result = unpadLuminancePlane(data, width, height, rowStride = width, pixelStride = 1)

        assertSame(data, result, "tightly packed plane should be returned without copying")
    }

    @Test
    fun `padded plane still strips row padding`() {
        val width = 640
        val height = 480
        val rowStride = 768 // 128 bytes of row padding
        val data = plane(width, height, rowStride)

        val result = unpadLuminancePlane(data, width, height, rowStride, pixelStride = 1)

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
        val data = plane(width, height, rowStride)

        val result = unpadLuminancePlane(data, width, height, rowStride, pixelStride)

        assertEquals(width * height, result.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(data[y * rowStride + x * pixelStride], result[y * width + x])
            }
        }
    }

    /**
     * The fix is a pure speedup: for every plane geometry the camera can hand us, the bytes the
     * scanner sees must be byte-identical to what the old code produced.
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
            val data = plane(width, height, rowStride)
            val fixed = unpadLuminancePlane(data, width, height, rowStride, pixelStride = 1)
            val legacy = legacyUnpad(data, width, height, rowStride, pixelStride = 1)

            // The fast path may hand back the backing array, which is longer than width*height
            // when the buffer is over-allocated; the scanner only reads the first width*height.
            assertTrue(fixed.size >= width * height, "${width}x$height/$rowStride too small")
            assertContentEquals(
                legacy.copyOf(width * height),
                fixed.copyOf(width * height),
                "content drift at ${width}x$height rowStride=$rowStride",
            )
        }
    }

    /**
     * Not a strict benchmark, but a regression tripwire: at 1080p the fast path should be orders of
     * magnitude cheaper than the per-pixel copy the old guard forced on every frame.
     */
    @Test
    fun `fast path is dramatically cheaper than the per-pixel copy`() {
        val width = 1920
        val height = 1080
        val data = plane(width, height, rowStride = width)

        repeat(3) {
            unpadLuminancePlane(data, width, height, width, 1)
            legacyUnpad(data, width, height, width, 1)
        }

        val iterations = 20
        val fixedNanos = measureNanoTime {
            repeat(iterations) { unpadLuminancePlane(data, width, height, width, 1) }
        } / iterations
        val legacyNanos = measureNanoTime {
            repeat(iterations) { legacyUnpad(data, width, height, width, 1) }
        } / iterations

        println(
            "LuminancePlane 1920x1080 packed: fixed=${fixedNanos / 1000}us " +
                "legacy=${legacyNanos / 1000}us speedup=${legacyNanos.toDouble() / fixedNanos.coerceAtLeast(1)}x"
        )

        assertTrue(
            fixedNanos * 10 < legacyNanos,
            "expected fast path to be >10x cheaper, got fixed=${fixedNanos}ns legacy=${legacyNanos}ns",
        )
    }
}
