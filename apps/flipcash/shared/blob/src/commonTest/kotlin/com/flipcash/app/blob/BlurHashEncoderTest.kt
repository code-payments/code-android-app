package com.flipcash.app.blob

import com.flipcash.shared.common.ui.BlurHash
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun `unusable input yields an empty hash`() {
        assertEquals("", BlurHashEncoder.encode(IntArray(0), 0, 0, 4, 3))
        assertEquals("", BlurHashEncoder.encode(IntArray(4), 4, 4, 4, 3))
        assertEquals("", BlurHashEncoder.encode(solid(0), 8, 8, 10, 3))
    }

    private fun thumbnail(sourceWidth: Int, sourceHeight: Int) =
        ArgbThumbnail(solid(0x336699), 8, 8, sourceWidth, sourceHeight)

    @Test
    fun `thumbnail hash is 4x3 for landscape and 3x4 for portrait`() {
        val landscape = BlurHashEncoder.fromThumbnail(thumbnail(200, 100))
        val portrait = BlurHashEncoder.fromThumbnail(thumbnail(100, 200))

        assertEquals(4 + 2 * 12, landscape.length)
        // size flag = (cx-1) + (cy-1)*9: 4x3 -> 21 -> 'L', 3x4 -> 29 -> 'T'
        assertEquals('L', landscape[0])
        assertEquals('T', portrait[0])
        assertEquals('L', BlurHashEncoder.fromThumbnail(thumbnail(100, 100))[0])
    }
}
