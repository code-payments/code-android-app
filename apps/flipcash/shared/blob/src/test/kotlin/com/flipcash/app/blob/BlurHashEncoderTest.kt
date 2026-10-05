package com.flipcash.app.blob

import android.graphics.Bitmap
import com.flipcash.shared.common.ui.BlurHash
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BlurHashEncoderTest {

    private fun solid(rgb: Int, w: Int = 8, h: Int = 8) = IntArray(w * h) { (0xFF shl 24) or rgb }

    @Test
    fun `hash length follows the component count`() {
        assertEquals(4 + 2 * 4 * 3, BlurHashEncoder.encode(solid(0x336699), 8, 8, 4, 3).length)
        assertEquals(4 + 2 * 3 * 4, BlurHashEncoder.encode(solid(0x336699), 8, 8, 3, 4).length)
    }

    @Test
    fun `a solid colour round-trips through the average colour`() {
        for (rgb in listOf(0x000000, 0xFFFFFF, 0x336699, 0xE8552B)) {
            val hash = BlurHashEncoder.encode(solid(rgb), 8, 8, 4, 3)

            val average = assertNotNull(BlurHash.averageColor(hash))
            for (shift in listOf(16, 8, 0)) {
                assertTrue(
                    abs((average shr shift and 255) - (rgb shr shift and 255)) <= 1,
                    "channel at $shift of ${rgb.toString(16)} came back ${average.toString(16)}",
                )
            }
        }
    }

    @Test
    fun `a gradient round-trips through the decoder`() {
        val w = 32
        val h = 24
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val r = x * 255 / (w - 1)
            (0xFF shl 24) or (r shl 16) or ((255 - r) shl 8) or 0x40
        }

        val hash = BlurHashEncoder.encode(pixels, w, h, 4, 3)
        val decoded = assertNotNull(BlurHash.decode(hash, w, h))

        val out = IntArray(w * h).also { decoded.getPixels(it, 0, w, 0, 0, w, h) }
        val meanError = pixels.indices.sumOf { i ->
            listOf(16, 8, 0).sumOf { s -> abs((pixels[i] shr s and 255) - (out[i] shr s and 255)) }
        } / (pixels.size * 3.0)
        // 12 DCT components of a horizontal ramp: soft, but nowhere near a different image.
        assertTrue(meanError < 20, "mean channel error $meanError")
    }

    @Test
    fun `unusable input yields an empty hash`() {
        assertEquals("", BlurHashEncoder.encode(IntArray(0), 0, 0, 4, 3))
        assertEquals("", BlurHashEncoder.encode(IntArray(4), 4, 4, 4, 3))
        assertEquals("", BlurHashEncoder.encode(solid(0), 8, 8, 10, 3))
    }

    @Test
    fun `thumbnail hash is 4x3 for landscape and 3x4 for portrait`() {
        val landscape = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val portrait = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)

        assertEquals(4 + 2 * 12, BlurHashEncoder.fromThumbnail(landscape, 200, 100).length)
        assertEquals("L".single(), BlurHashEncoder.fromThumbnail(landscape, 200, 100)[0])
        // size flag = (cx-1) + (cy-1)*9: 4x3 -> 21 -> 'L', 3x4 -> 29 -> 'T'
        assertEquals('T', BlurHashEncoder.fromThumbnail(portrait, 100, 200)[0])
    }
}
