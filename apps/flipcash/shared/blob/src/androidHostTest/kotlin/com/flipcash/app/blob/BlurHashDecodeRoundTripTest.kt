package com.flipcash.app.blob

import com.flipcash.shared.common.ui.BlurHash
import com.flipcash.shared.common.ui.decode
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Needs the Android `Bitmap` that the decoder returns. */
@RunWith(RobolectricTestRunner::class)
class BlurHashDecodeRoundTripTest {

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
    }}
