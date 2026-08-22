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
import kotlin.math.atan
import kotlin.system.measureNanoTime
import kotlin.math.tan

/**
 * How small can a code get before the scanner stops decoding it?
 *
 * The native detector normalises its minimum feature sizes to a 480px baseline
 * (`scaling_rate = MIN(rows, cols) / 480`), so the smallest decodable code shrinks as the analysis
 * frame grows -- but only linearly in one term and quadratically in another, and the data rings put
 * their own floor under it. Reasoning about the constants gives a bound, not an answer. This
 * measures it.
 *
 * The result converts straight into scan range: a code of physical width `W` decodes out to
 * `W / (2 * tan(theta / 2))`, where `theta` is the angular size the measurement lands on for a given
 * analysis resolution and camera field of view.
 *
 * These frames are synthetic and ideal -- perfect focus, no motion blur, no sensor noise, dead-on
 * perpendicular, maximum contrast. Real range is strictly worse. The number to trust here is the
 * *ratio* between resolutions, not the absolute distance.
 */
@RunWith(AndroidJUnit4::class)
class KikCodeRangeTest {

    private val renderer = KikCodeContentRendererImpl().apply {
        badge = requireNotNull(
            ContextCompat.getDrawable(
                InstrumentationRegistry.getInstrumentation().context,
                com.kik.kikx.test.R.drawable.ic_logo_round_white,
            )
        )
    }
    private val scanner = KikCodeScannerImpl()

    /**
     * What the analysis stream might be, paired with the slice of horizontal field of view each one
     * actually covers.
     *
     * The sensor is 4:3, so how much of the lens's 72.5-degree horizontal sweep lands in the frame
     * depends on the aspect ratio, not just the pixel count: 16:9 and 4:3 streams keep the full
     * width and crop vertically, while a square stream throws away a quarter of the width and is
     * left with the vertical field of view instead. That crop is invisible in the pixel dimensions
     * and is exactly what makes the shipping configuration worse than its short side suggests.
     */
    private data class AnalysisSize(val width: Int, val height: Int, val hfovDegrees: Double)

    private val resolutions = listOf(
        // What Seeker actually negotiates today for the deprecated 1920x1080 request.
        AnalysisSize(720, 720, SQUARE_HFOV_DEGREES),
        // What the preview gets: full width, 4:3.
        AnalysisSize(1440, 1080, FULL_HFOV_DEGREES),
        AnalysisSize(1280, 720, FULL_HFOV_DEGREES),
        // What the code asks for.
        AnalysisSize(1920, 1080, FULL_HFOV_DEGREES),
        AnalysisSize(2560, 1440, FULL_HFOV_DEGREES),
        AnalysisSize(3840, 2160, FULL_HFOV_DEGREES),
    )

    private fun encodeRemoteCode(seed: Int): Pair<ByteArray, ByteArray> {
        val payload = ByteArray(REMOTE_PAYLOAD_BYTES) { ((it * 7 + seed) and 0xFF).toByte() }
        return payload to requireNotNull(Scanner.encode(payload)) { "native encode returned null" }
    }

    /** Renders [encoded] at exactly [codePx] wide, centred in a packed `width x height` Y plane. */
    private fun renderFrame(encoded: ByteArray, width: Int, height: Int, codePx: Int): ByteArray {
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

        val plane = ByteArray(width * height)
        for (i in 0 until width * height) {
            val p = pixels[i]
            plane[i] = (
                (
                    77 * ((p shr 16) and 0xFF) +
                        150 * ((p shr 8) and 0xFF) +
                        29 * (p and 0xFF)
                    ) shr 8
                ).toByte()
        }
        return plane
    }

    private fun decodes(encoded: ByteArray, payload: ByteArray, w: Int, h: Int, codePx: Int): Boolean {
        val plane = renderFrame(encoded, w, h, codePx)
        val converted = LuminancePlane.unpad(plane, w, h, w, 1)
        val result = runBlocking {
            scanner.scanKikCode(converted, w, h, ScanQuality.Best).getOrNull()
        }
        return result is ScannableKikCode.RemoteKikCode && result.payloadId.contentEquals(payload)
    }

    /**
     * Smallest code width in pixels that still decodes, or -1 if even a large one fails.
     *
     * Bisects on the assumption that decodability is monotone in size, then walks down from the
     * bisection result to catch a threshold that is ragged rather than sharp -- it keeps stepping
     * until [GIVE_UP_RUN] consecutive sizes fail, so an isolated miss above the true floor does not
     * end the search early.
     */
    private fun minDecodablePx(encoded: ByteArray, payload: ByteArray, w: Int, h: Int): Int {
        var hi = (minOf(w, h) * 0.9f).toInt()
        if (!decodes(encoded, payload, w, h, hi)) return -1

        var lo = MIN_PROBE_PX
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (decodes(encoded, payload, w, h, mid)) hi = mid else lo = mid + 1
        }

        var best = hi
        var run = 0
        var size = hi - 1
        while (size >= MIN_PROBE_PX && run < GIVE_UP_RUN) {
            if (decodes(encoded, payload, w, h, size)) {
                best = size
                run = 0
            } else {
                run++
            }
            size--
        }
        return best
    }

    @Test
    fun smallestDecodableCodeByAnalysisResolution() {
        val seeds = listOf(3, 11, 29)

        for ((w, h, hfov) in resolutions) {
            val perSeed = seeds.map { seed ->
                val (payload, encoded) = encodeRemoteCode(seed)
                minDecodablePx(encoded, payload, w, h)
            }
            val worst = perSeed.max()

            // Angular size of the smallest decodable code, given the frame spans hfov degrees.
            val degrees = worst.toDouble() / w * hfov
            // A code of physical width W decodes out to W / (2 tan(theta/2)).
            val rangeFactor = 1.0 / (2.0 * tan(Math.toRadians(degrees) / 2.0))

            Log.i(
                TAG,
                "res=${w}x$h hfov=${"%.1f".format(hfov)} minCodePx=$worst (seeds=$perSeed) " +
                    "angular=${"%.3f".format(degrees)}deg " +
                    "range=${"%.2f".format(rangeFactor)}x code width",
            )
        }

        // Sanity floor: the detector cannot need more than half the frame to work, or the sweep
        // above measured something other than what it thinks it did.
        val target = resolutions.first { it.width == 1920 }
        val (payload, encoded) = encodeRemoteCode(3)
        val at1080 = minDecodablePx(encoded, payload, target.width, target.height)
        require(at1080 in MIN_PROBE_PX..(target.height / 2)) { "implausible 1080p floor: $at1080" }
    }

    /**
     * What the extra pixels cost per frame.
     *
     * Range is only half the trade: the detector runs an unsharp mask twice, a threshold, and a
     * contour pass over the *whole* frame, so its work scales with area. If 1080p cannot keep up
     * with the camera the analyzer starts dropping frames, and dropped frames cost range too --
     * just further downstream, where it is much harder to see.
     *
     * Both a frame with a code and a frame of ellipse clutter are timed. The clutter case is the
     * one that matters: a code is found early and short-circuits, while clutter makes the detector
     * evaluate and reject every candidate, which is exactly what a phone pointed at a room does.
     */
    @Test
    fun detectorThroughputByResolution() {
        val (_, encoded) = encodeRemoteCode(3)

        // Prepared up front so frame construction never lands inside a timed section.
        val frames = resolutions.associate { (w, h, _) ->
            (w to h) to Pair(
                LuminancePlane.unpad(
                    renderFrame(encoded, w, h, (minOf(w, h) * 0.4f).toInt()), w, h, w, 1,
                ),
                LuminancePlane.unpad(clutterPlane(w, h), w, h, w, 1),
            )
        }

        for ((w, h, _) in resolutions) {
            val (code, clutter) = frames.getValue(w to h)
            repeat(WARMUP_FRAMES) {
                runBlocking { scanner.scanKikCode(code, w, h, ScanQuality.Best) }
                runBlocking { scanner.scanKikCode(clutter, w, h, ScanQuality.Best) }
            }
        }

        // Round-robin rather than resolution-by-resolution. Timing each resolution to completion in
        // turn lets CPU frequency scaling drift across the run and attribute itself to whichever
        // resolution happened to be measured while the clocks were low -- which is how an 8MP frame
        // ends up "faster" than a 1.5MP one. Interleaving spreads any drift evenly.
        val codeSamples = resolutions.associate { (w, h, _) -> (w to h) to mutableListOf<Double>() }
        val clutterSamples = resolutions.associate { (w, h, _) -> (w to h) to mutableListOf<Double>() }

        repeat(TIMED_FRAMES) {
            for ((w, h, _) in resolutions) {
                val (code, clutter) = frames.getValue(w to h)
                codeSamples.getValue(w to h) += measureNanoTime {
                    runBlocking { scanner.scanKikCode(code, w, h, ScanQuality.Best) }
                } / 1_000_000.0
                clutterSamples.getValue(w to h) += measureNanoTime {
                    runBlocking { scanner.scanKikCode(clutter, w, h, ScanQuality.Best) }
                } / 1_000_000.0
            }
        }

        for ((w, h, _) in resolutions) {
            val codeMs = codeSamples.getValue(w to h).sorted().let { it[it.size / 2] }
            val clutterMs = clutterSamples.getValue(w to h).sorted().let { it[it.size / 2] }
            val worst = maxOf(codeMs, clutterMs)
            Log.i(
                TAG,
                "res=${w}x$h megapixels=${"%.2f".format(w * h / 1_000_000.0)} " +
                    "code=${"%.1f".format(codeMs)}ms clutter=${"%.1f".format(clutterMs)}ms " +
                    "maxSustainedFps=${"%.1f".format(1000.0 / worst)}",
            )
        }
    }

    /**
     * A frame full of ellipses that are not a code -- the detector's worst case, since every one is
     * a candidate it must evaluate and discard. Deterministic so runs are comparable.
     */
    private fun clutterPlane(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }
        var state = 0x2545F491
        fun next(bound: Int): Int {
            state = state * 1103515245 + 12345
            return ((state ushr 16) and 0x7FFF) % bound
        }
        repeat(CLUTTER_BLOBS) {
            paint.color = if (next(2) == 0) Color.WHITE else Color.GRAY
            val r = (next(minOf(width, height) / 16) + 6).toFloat()
            canvas.drawOval(
                next(width) - r, next(height) - r, next(width) + r, next(height) + r, paint,
            )
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return ByteArray(width * height) { i ->
            val p = pixels[i]
            (
                (
                    77 * ((p shr 16) and 0xFF) +
                        150 * ((p shr 8) and 0xFF) +
                        29 * (p and 0xFF)
                    ) shr 8
                ).toByte()
        }
    }

    /**
     * The other half of the range equation: how many pixels a degree of field of view is worth.
     * Logged from the camera the scanner actually binds so the conversion above is anchored to this
     * device rather than to an assumed lens.
     */
    @Test
    fun reportCameraGeometry() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = ctx.getSystemService(android.hardware.camera2.CameraManager::class.java)
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
            if (facing != android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) continue
            val focal = chars.get(
                android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            )?.firstOrNull() ?: continue
            val size = chars.get(
                android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
            ) ?: continue
            val hfov = 2 * Math.toDegrees(atan((size.width / 2.0) / focal))
            Log.i(TAG, "camera $id back f=${focal}mm sensor=${size.width}x${size.height}mm hFOV=${"%.1f".format(hfov)}deg")
        }
    }

    private companion object {
        const val TAG = "KikCodeRange"
        const val REMOTE_PAYLOAD_BYTES = 20
        const val MIN_PROBE_PX = 24
        const val GIVE_UP_RUN = 12
        const val WARMUP_FRAMES = 5
        const val TIMED_FRAMES = 25
        const val CLUTTER_BLOBS = 120

        /**
         * Horizontal field of view of the back camera this device binds, in degrees, when the
         * stream keeps the sensor's full width. Confirmed on device by [reportCameraGeometry].
         */
        const val FULL_HFOV_DEGREES = 72.5

        /**
         * The same lens seen through a square stream. A 1:1 crop of a 4:3 sensor keeps the full
         * height and only three quarters of the width, so it sees the *vertical* field of view --
         * a fifth of the horizontal sweep simply is not in the frame to be scanned.
         */
        const val SQUARE_HFOV_DEGREES = 57.6
    }
}
