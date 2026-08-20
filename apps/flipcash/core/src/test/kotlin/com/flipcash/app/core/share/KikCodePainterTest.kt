package com.flipcash.app.core.share

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.getcode.codes.kikcode.kikCodeBitmap
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the Android *painter* — the half of code rendering that the cross-platform vectors can't
 * reach. `:libs:codes:kikcode` gates the geometry and the SVG on both toolchains; these assertions
 * gate the translation of that geometry into `Canvas` draw calls, where an inverted arc sweep or a
 * mis-scaled badge would be invisible to the vectors.
 *
 * Native graphics so `Canvas` actually rasterises rather than recording no-ops.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class KikCodePainterTest {

    /**
     * The full 35-byte payload with every bit set -> all six rings full, i.e. the largest graphic
     * the geometry can produce. A 20-byte tip payload leaves the outermost ring empty (24 bytes of
     * finder+payload is 192 bits, and ring 5 starts at bit 240), so it can't measure the framing.
     */
    private val saturated = ByteArray(35) { 0xFF.toByte() }

    /** Irregular, so runs, isolated dots and gaps all appear. */
    private val scattered = ByteArray(20) { (it * 7 + 1).toByte() }

    @Test
    fun `renders marks`() {
        val bitmap = kikCodeBitmap(scattered, SIZE)
        assertEquals(SIZE, bitmap.width)
        assertEquals(SIZE, bitmap.height)
        assertTrue(bitmap.opaquePixelCount() > 0, "nothing was drawn")
    }

    @Test
    fun `graphic fills its box without overflowing it`() {
        // The outermost stroke reaches ~0.939 of the radius by construction (ring centre 0.90625
        // plus half a 0.0656 stroke). Both bounds matter: the upper one catches clipping, and the
        // lower one catches a re-introduced inset -- Android used to shrink the code to 0.93 and
        // then overscan the view by 1.03 to compensate, which put it ~4% under iOS.
        val extent = kikCodeBitmap(saturated, SIZE).maxOpaqueRadius() / (SIZE / 2.0)
        assertTrue(extent in 0.93..0.96, "outer extent was $extent of the radius")
    }

    @Test
    fun `centre well is left empty when there is no badge`() {
        val bitmap = kikCodeBitmap(scattered, SIZE)
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(SIZE / 2, SIZE / 2))
    }

    @Test
    fun `badge is drawn into the centre well`() {
        val bitmap = kikCodeBitmap(scattered, SIZE, badge = ColorDrawable(Color.RED))
        assertEquals(Color.RED, bitmap.getPixel(SIZE / 2, SIZE / 2))

        // The well is INNER_RING_RATIO (0.32) of the outer radius, and the badge fills it exactly.
        val expected = (SIZE / 2.0) * 0.32
        val actual = bitmap.run {
            var left = width
            for (x in 0 until width) {
                if (getPixel(x, height / 2) == Color.RED) { left = x; break }
            }
            width / 2.0 - left
        }
        assertTrue(kotlin.math.abs(actual - expected) <= 1.0, "badge half-width $actual, want $expected")
    }

    @Test
    fun `output scales linearly with size`() {
        val small = kikCodeBitmap(saturated, SIZE).maxOpaqueRadius() / SIZE
        val large = kikCodeBitmap(saturated, SIZE * 2).maxOpaqueRadius() / (SIZE * 2)
        assertTrue(kotlin.math.abs(small - large) < 0.005, "extent $small vs $large")
    }

    private fun Bitmap.pixels(): IntArray =
        IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

    private fun Bitmap.opaquePixelCount(): Int = pixels().count { Color.alpha(it) > ALPHA_FLOOR }

    /** Distance from the centre to the furthest drawn pixel, in px. */
    private fun Bitmap.maxOpaqueRadius(): Double {
        val centre = width / 2.0
        val pixels = pixels()
        var furthest = 0.0
        for (index in pixels.indices) {
            if (Color.alpha(pixels[index]) <= ALPHA_FLOOR) continue
            val distance = hypot(index % width - centre, (index / width) - centre)
            if (distance > furthest) furthest = distance
        }
        return furthest
    }

    private companion object {
        const val SIZE = 512

        // Ignore antialiasing fringes so the extent measurement tracks the shape, not its feather.
        const val ALPHA_FLOOR = 128
    }
}
