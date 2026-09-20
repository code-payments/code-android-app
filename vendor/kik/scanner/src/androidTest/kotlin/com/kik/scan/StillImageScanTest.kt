package com.kik.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.codes.kikcode.LuminancePlane
import com.getcode.codes.kikcode.StillImageCodeSearch
import com.getcode.media.StaticImageResult
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import com.kik.kikx.kincodes.KikCodeContentRendererImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The still-image half of the scanner harness. [KikCodeScanTest] covers the camera path; this
 * covers what a picked photo goes through, which shares the decoder and nothing else.
 */
@RunWith(AndroidJUnit4::class)
class StillImageScanTest {

    // Constructed exactly as `KikCodeScanTest` constructs it -- copy that line rather than this
    // one if the two disagree.
    private val scanner = KikCodeScannerImpl()

    @Test
    fun codeFillingTheFrameDecodesOnTheFirstTier() = runBlocking {
        val bitmap = renderScene(width = 1200, height = 1200, codeSide = 1000)

        val result = search(bitmap)

        assertTrue("expected a code, got $result", result is StaticImageResult.Found)
        assertEquals(1, (result as StaticImageResult.Found).tier)
    }

    @Test
    fun smallOffCentreCodeDecodesFromALaterTier() = runBlocking {
        // Sized so the first tier genuinely cannot do it. Tier 1 renders the whole frame clamped
        // to MAX_RENDERED_SIDE, so 3200 wide is halved and the code arrives at 130px; the top-left
        // quadrant is 1600 wide, renders 1:1, and hands the scanner the same 260px the
        // frame-filling case proves is readable. Until this scene was widened the test decoded on
        // tier 1 and its name was aspirational -- nothing exercised the quadrants end to end.
        val bitmap = renderScene(
            width = 3200,
            height = 2400,
            codeSide = 260,
            left = 120,
            top = 700,
        )

        val result = search(bitmap)

        assertTrue("expected a code, got $result", result is StaticImageResult.Found)
        // The whole 1600x1200 frame is one 260px code on black, which the first tier cannot
        // resolve -- this is the case the ladder exists for, so name the tier it took.
        assertTrue(
            "expected a later tier, got $result",
            (result as StaticImageResult.Found).tier > 1,
        )
    }

    @Test
    fun anImageWithNoCodeFindsNothing() = runBlocking {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(Color.DKGRAY)
        }

        assertEquals(StaticImageResult.NotFound, search(bitmap))
    }

    @Test
    fun aFruitlessSearchStaysWithinTheBudget() = runBlocking {
        // Large enough that the full ladder cannot finish in the budget, so what ends the search
        // is the deadline rather than the candidates running out.
        val bitmap = Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(Color.DKGRAY)
        }

        val started = System.currentTimeMillis()
        val result = searchWithBudget(bitmap, budgetMillis = 500)
        val elapsed = System.currentTimeMillis() - started

        assertEquals(StaticImageResult.Exhausted, result)
        // One crop may overrun, since the budget is checked between candidates rather than inside
        // the native scanner. The margin is for that, not for slack.
        assertTrue("budget of 500ms took ${elapsed}ms", elapsed < 3000)
    }

    // MARK: helpers

    /**
     * Walks the ladder exactly as [com.getcode.media.StaticImageAnalyzerImpl] does, against a
     * bitmap rather than a Uri, so the test needs no content resolver.
     */
    private suspend fun search(bitmap: Bitmap): StaticImageResult =
        searchWithBudget(bitmap, budgetMillis = Long.MAX_VALUE)

    private suspend fun searchWithBudget(bitmap: Bitmap, budgetMillis: Long): StaticImageResult {
        val started = System.currentTimeMillis()

        StillImageCodeSearch.candidates(bitmap.width, bitmap.height).forEach { candidate ->
            if (System.currentTimeMillis() - started > budgetMillis) {
                return StaticImageResult.Exhausted
            }

            val longestSide = maxOf(candidate.width, candidate.height)
            val renderedSide = minOf(
                longestSide * candidate.zoom,
                StillImageCodeSearch.MAX_RENDERED_SIDE.toFloat(),
            )
            val scale = renderedSide / longestSide
            val width = maxOf((candidate.width * scale).toInt(), 1)
            val height = maxOf((candidate.height * scale).toInt(), 1)

            val crop = Bitmap.createBitmap(
                bitmap,
                candidate.left,
                candidate.top,
                candidate.width,
                candidate.height,
            )
            val scaled = Bitmap.createScaledBitmap(crop, width, height, true)

            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)

            if (crop != bitmap) crop.recycle()
            if (scaled != crop && scaled != bitmap) scaled.recycle()

            val luminance = LuminancePlane.fromArgb(pixels, width, height)
            val scanned = scanner.scanKikCode(luminance, width, height).getOrNull()

            if (scanned != null) {
                return StaticImageResult.Found(scanned, candidate.tier, candidate.zoom)
            }
        }

        return StaticImageResult.NotFound
    }

    /**
     * A code drawn on black. Polarity matches [KikCodeScanTest]: the detector thresholds for
     * bright blobs and fits an ellipse to the centre badge, so black-on-white leaves the badge as
     * a dark hole and nothing is ever found. The badge well must be filled with the real logo
     * artwork for the same reason -- see [KikCodeScanTest]'s note on it.
     */
    private fun renderScene(
        width: Int,
        height: Int,
        codeSide: Int,
        left: Int = (width - codeSide) / 2,
        top: Int = (height - codeSide) / 2,
    ): Bitmap {
        val renderer = KikCodeContentRendererImpl().apply {
            badge = requireNotNull(
                ContextCompat.getDrawable(
                    InstrumentationRegistry.getInstrumentation().context,
                    com.kik.kikx.test.R.drawable.ic_logo_round_white,
                )
            )
        }
        val encoded = requireNotNull(Scanner.encode(PAYLOAD)) { "native encode returned null" }

        val code = Bitmap.createBitmap(codeSide, codeSide, Bitmap.Config.ARGB_8888)
        Canvas(code).also { canvas ->
            canvas.drawColor(Color.BLACK)
            renderer.render(encoded, codeSide, canvas)
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { scene ->
            Canvas(scene).also { canvas ->
                canvas.drawColor(Color.BLACK)
                canvas.drawBitmap(code, left.toFloat(), top.toFloat(), Paint())
            }
            code.recycle()
        }
    }

    private companion object {
        /** A remote code payload is 20 bytes, the same shape `KikCodeScanTest` encodes. */
        val PAYLOAD = ByteArray(20) { ((it * 7 + 11) % 251).toByte() }
    }
}
