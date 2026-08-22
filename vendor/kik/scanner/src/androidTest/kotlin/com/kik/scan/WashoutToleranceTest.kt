package com.kik.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.getcode.codes.kikcode.LuminancePlane
import com.kik.kikx.kikcodes.ScanQuality
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import com.kik.kikx.kincodes.KikCodeContentRendererImpl
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * How much exposure error the detector survives.
 *
 * Candidate finding is built entirely on one line of `scanner.cpp`:
 *
 *     threshold(greyscale, whitish, 170, 255, THRESH_BINARY);
 *
 * Every contour, every ellipse, and therefore every code the scanner ever finds comes out of that
 * binary image. The cutoff is an absolute luminance value, not a local or adaptive one, and it runs
 * after two unsharp passes that push highlights further up. So the detector does not ask whether the
 * code has contrast -- it asks whether the code's light ink lands above 170 and its dark ink lands
 * below it. A frame can be perfectly sharp, perfectly framed and perfectly in focus and still be
 * undetectable simply for sitting at the wrong exposure.
 *
 * That makes auto-exposure part of the scanner, and the two platforms configure it differently:
 * iOS pins `exposurePointOfInterest` to the centre of the frame, Android leaves metering to the
 * camera's whole-frame default and never requests AE at all. Pointed at a bright phone screen in a
 * dim room, whole-frame metering exposes for the dark surround and blows the screen out.
 *
 * This measures the window rather than deriving it, because the unsharp passes move the boundary
 * and only a measurement says by how much.
 *
 * Frames are synthetic and otherwise ideal, so these bounds are the generous end of what a real
 * camera sees.
 */
@RunWith(AndroidJUnit4::class)
class WashoutToleranceTest {

    private val renderer = KikCodeContentRendererImpl().apply {
        badge = requireNotNull(
            ContextCompat.getDrawable(
                InstrumentationRegistry.getInstrumentation().context,
                com.kik.kikx.test.R.drawable.ic_logo_round_white,
            )
        )
    }
    private val scanner = KikCodeScannerImpl()

    private fun encodeRemoteCode(seed: Int): Pair<ByteArray, ByteArray> {
        val payload = ByteArray(REMOTE_PAYLOAD_BYTES) { ((it * 7 + seed) and 0xFF).toByte() }
        return payload to requireNotNull(Scanner.encode(payload)) { "native encode returned null" }
    }

    /**
     * Renders a code into a Y plane whose full black-to-white range has been squeezed into
     * [blackLevel]..[whiteLevel].
     *
     * This is what an exposure error does to a frame. Overexposure lifts the whole range towards
     * white and clips it there; underexposure crushes it towards black; veiling glare off a bright
     * emissive panel lifts the floor without moving the ceiling. All three are the same
     * transformation with different endpoints, and all three are applied to the whole frame,
     * because a camera's exposure is a property of the frame and not of the subject.
     */
    private fun renderAtLevels(
        encoded: ByteArray,
        width: Int,
        height: Int,
        codePx: Int,
        blackLevel: Int,
        whiteLevel: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        canvas.save()
        canvas.translate((width - codePx) / 2f, (height - codePx) / 2f)
        renderer.render(encoded, codePx, canvas)
        canvas.restore()

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        // Precomputed so the remap is a table lookup rather than arithmetic per pixel; these
        // sweeps render hundreds of multi-megapixel frames.
        val span = whiteLevel - blackLevel
        val map = ByteArray(256) { luma ->
            (blackLevel + luma * span / 255).coerceIn(0, 255).toByte()
        }

        val plane = ByteArray(width * height)
        for (i in 0 until width * height) {
            val p = pixels[i]
            val luma = (
                (
                    77 * ((p shr 16) and 0xFF) +
                        150 * ((p shr 8) and 0xFF) +
                        29 * (p and 0xFF)
                    ) shr 8
                )
            plane[i] = map[luma]
        }
        return plane
    }

    private fun decodesAtLevels(
        encoded: ByteArray,
        payload: ByteArray,
        blackLevel: Int,
        whiteLevel: Int,
    ): Boolean {
        val plane = renderAtLevels(encoded, WIDTH, HEIGHT, CODE_PX, blackLevel, whiteLevel)
        val converted = LuminancePlane.unpad(plane, WIDTH, HEIGHT, WIDTH, 1)
        val result = runBlocking {
            scanner.scanKikCode(converted, WIDTH, HEIGHT, ScanQuality.Best).getOrNull()
        }
        return result is ScannableKikCode.RemoteKikCode && result.payloadId.contentEquals(payload)
    }

    /**
     * Overexposure: the highlights are already clipped at white and the floor keeps rising.
     *
     * This is the reported failure. A phone screen at full brightness in a dim room is the worst
     * case a payment scanner has, because whole-frame metering averages in all that surrounding
     * darkness and drives the exposure up until the screen is a white slab.
     */
    @Test
    fun overexposureFloor() {
        val (payload, encoded) = encodeRemoteCode(3)
        var highestDecodable = -1
        for (black in 0..250 step 10) {
            val ok = decodesAtLevels(encoded, payload, black, 255)
            Log.i(TAG, "overexposed black=$black white=255 decoded=$ok")
            if (ok) highestDecodable = black else break
        }
        Log.i(TAG, "RESULT overexposure: highest decodable black level = $highestDecodable")

        assertTrue(
            highestDecodable >= 0,
            "the detector failed even on a correctly exposed frame -- the sweep is measuring " +
                "something other than exposure",
        )
    }

    /** Underexposure, for symmetry: the floor is at black and the ceiling keeps falling. */
    @Test
    fun underexposureCeiling() {
        val (payload, encoded) = encodeRemoteCode(3)
        var lowestDecodable = -1
        for (white in 255 downTo 5 step 10) {
            val ok = decodesAtLevels(encoded, payload, 0, white)
            Log.i(TAG, "underexposed black=0 white=$white decoded=$ok")
            if (ok) lowestDecodable = white else break
        }
        Log.i(TAG, "RESULT underexposure: lowest decodable white level = $lowestDecodable")
    }

    /**
     * Contrast held around a mid-grey, so the band closes in on 128 from both sides.
     *
     * If the detector adapted to the frame it would keep working here until the contrast fell into
     * the noise. If it is pinned to an absolute cutoff it will instead fail the moment the band
     * stops straddling that cutoff, while the code is still obviously legible. Which of those two
     * happens is the whole question.
     */
    @Test
    fun contrastAroundMidGrey() {
        val (payload, encoded) = encodeRemoteCode(3)
        var lowestDecodable = -1
        for (half in 128 downTo 5 step 5) {
            val ok = decodesAtLevels(encoded, payload, 128 - half, 128 + half)
            Log.i(TAG, "midgrey black=${128 - half} white=${128 + half} span=${2 * half} decoded=$ok")
            if (ok) lowestDecodable = 2 * half else break
        }
        Log.i(TAG, "RESULT mid-grey: smallest decodable contrast span = $lowestDecodable of 255")
    }

    private companion object {
        const val TAG = "KikCodeRange"
        const val REMOTE_PAYLOAD_BYTES = 20
        const val WIDTH = 1920
        const val HEIGHT = 1080

        /**
         * A comfortably large code -- roughly 40% of the frame height, far above the size floor
         * measured in `KikCodeRangeTest`. Size must not be the thing under test here.
         */
        const val CODE_PX = 432
    }
}
