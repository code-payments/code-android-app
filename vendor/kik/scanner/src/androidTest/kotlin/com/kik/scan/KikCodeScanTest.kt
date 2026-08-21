package com.kik.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getcode.util.unpadLuminancePlane
import com.kik.kikx.kikcodes.ScanQuality
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import com.kik.kikx.kincodes.KikCodeContentRendererImpl
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end sweep over rendered Kik codes.
 *
 * Follows the production pipeline exactly: [Scanner.encode] produces the same encoded bytes the
 * backend hands the bill UI, [KikCodeContentRendererImpl] draws them through the shared geometry,
 * the result is packed into a synthetic YUV_420_888 Y plane, run through the same
 * [unpadLuminancePlane] conversion the camera analyzer uses, and handed to the native scanner.
 *
 * Results are logged under [TAG].
 */
@RunWith(AndroidJUnit4::class)
class KikCodeScanTest {

    /**
     * The detector locates a code by its centre ellipse, so the badge well must be filled — in the
     * app that is the round logo drawable. An empty well is simply not scannable.
     */
    private val renderer = KikCodeContentRendererImpl().apply {
        badge = ShapeDrawable(OvalShape()).apply { paint.color = Color.WHITE }
    }
    private val scanner = KikCodeScannerImpl()

    /** Analysis resolutions the app requests, plus common fallbacks. */
    private val resolutions = listOf(
        640 to 480,
        1280 to 720,
        1920 to 1080,
    )

    /** Fraction of the frame's short side the code graphic occupies. */
    private val codeScales = listOf(0.5f, 0.7f, 0.9f)

    private data class Frame(
        val data: ByteArray,
        val width: Int,
        val height: Int,
        val rowStride: Int,
    )

    /** A remote code payload is 20 bytes; encode it the way the backend does. */
    private fun encodeRemoteCode(seed: Int): Pair<ByteArray, ByteArray> {
        val payload = ByteArray(REMOTE_PAYLOAD_BYTES) { ((it * 7 + seed) and 0xFF).toByte() }
        val encoded = requireNotNull(Scanner.encode(payload)) { "native encode returned null" }
        return payload to encoded
    }

    /**
     * Renders [encoded] centred in a `width x height` frame and returns it as a Y plane with
     * [rowPadding] bytes of stride padding per row — i.e. the shape the camera hands us.
     */
    private fun renderFrame(
        encoded: ByteArray,
        width: Int,
        height: Int,
        scale: Float,
        rowPadding: Int,
    ): Frame {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val codeSize = (minOf(width, height) * scale).toInt()
        canvas.save()
        canvas.translate((width - codeSize) / 2f, (height - codeSize) / 2f)
        renderer.render(encoded, codeSize, canvas)
        canvas.restore()

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        val rowStride = width + rowPadding
        val plane = ByteArray(rowStride * height)
        for (y in 0 until height) {
            val rowStart = y * rowStride
            val pixelRow = y * width
            for (x in 0 until width) {
                val p = pixels[pixelRow + x]
                // BT.601 luma, matching what the camera produces for the Y plane.
                val luma = (
                    77 * ((p shr 16) and 0xFF) +
                        150 * ((p shr 8) and 0xFF) +
                        29 * (p and 0xFF)
                    ) shr 8
                plane[rowStart + x] = luma.toByte()
            }
        }
        return Frame(plane, width, height, rowStride)
    }

    private fun scan(frame: Frame): ScannableKikCode? {
        val converted = unpadLuminancePlane(
            data = frame.data,
            width = frame.width,
            height = frame.height,
            rowStride = frame.rowStride,
            pixelStride = 1,
        )
        return runBlocking {
            scanner.scanKikCode(converted, frame.width, frame.height, ScanQuality.Best).getOrNull()
        }
    }

    @Test
    fun sweepRenderedCodesAcrossResolutionsAndStrides() {
        val (payload, encoded) = encodeRemoteCode(seed = 3)

        var attempts = 0
        var decoded = 0
        val failures = mutableListOf<String>()

        for ((width, height) in resolutions) {
            for (scale in codeScales) {
                // rowPadding 0 exercises the fast path; 64 exercises the unpadding copy.
                for (rowPadding in listOf(0, 64)) {
                    val frame = renderFrame(encoded, width, height, scale, rowPadding)
                    val label = "${width}x$height scale=$scale rowStride=${frame.rowStride}"
                    attempts++

                    val result = scan(frame)
                    if (result is ScannableKikCode.RemoteKikCode &&
                        result.payloadId.contentEquals(payload)
                    ) {
                        decoded++
                        Log.i(TAG, "DECODED  $label")
                    } else {
                        failures += label
                        Log.w(TAG, "MISSED   $label -> $result")
                    }
                }
            }
        }

        Log.i(TAG, "sweep: $decoded/$attempts decoded")
        assertTrue(
            failures.isEmpty(),
            "scanner failed to decode rendered code at: ${failures.joinToString()}",
        )
    }

    /**
     * The padded and packed representations of the same frame must decode identically — this is what
     * proves the fast path is a pure speedup and not a behaviour change.
     */
    @Test
    fun paddedAndPackedPlanesDecodeIdentically() {
        val (payload, encoded) = encodeRemoteCode(seed = 11)

        for ((width, height) in resolutions) {
            val packed = renderFrame(encoded, width, height, 0.7f, rowPadding = 0)
            val padded = renderFrame(encoded, width, height, 0.7f, rowPadding = 128)

            // Sanity: the fast path really is taken for the packed frame and not for the padded one.
            assertTrue(
                unpadLuminancePlane(packed.data, width, height, packed.rowStride, 1) === packed.data,
                "${width}x$height packed frame should take the fast path",
            )
            assertTrue(
                unpadLuminancePlane(padded.data, width, height, padded.rowStride, 1) !== padded.data,
                "${width}x$height padded frame should be unpadded",
            )

            val fromPacked = scan(packed)
            val fromPadded = scan(padded)
            Log.i(TAG, "stride parity ${width}x$height: packed=$fromPacked padded=$fromPadded")

            assertTrue(
                fromPacked is ScannableKikCode.RemoteKikCode &&
                    fromPacked.payloadId.contentEquals(payload),
                "packed frame did not decode at ${width}x$height",
            )
            // RemoteKikCode is a data class over a ByteArray, so its generated equals() compares
            // array identity -- compare contents explicitly.
            assertTrue(
                fromPadded is ScannableKikCode.RemoteKikCode &&
                    fromPadded.payloadId.contentEquals(payload),
                "padded frame did not decode at ${width}x$height",
            )
            assertEquals(
                (fromPacked as ScannableKikCode.RemoteKikCode).colorIndex,
                fromPadded.colorIndex,
                "colour drift at ${width}x$height",
            )
        }
    }

    /**
     * Measures the per-frame Y-plane conversion cost on-device: the fast path vs. the per-pixel copy
     * the old `pixelStride != -1` guard forced on every frame.
     */
    @Test
    fun benchmarkPerFrameConversion() {
        for ((width, height) in resolutions) {
            val data = ByteArray(width * height) { (it % 251).toByte() }

            repeat(5) {
                unpadLuminancePlane(data, width, height, width, 1)
                legacyUnpad(data, width, height, width, 1)
            }

            val iterations = 30
            val fastNanos = measureNanoTime {
                repeat(iterations) { unpadLuminancePlane(data, width, height, width, 1) }
            } / iterations
            val legacyNanos = measureNanoTime {
                repeat(iterations) { legacyUnpad(data, width, height, width, 1) }
            } / iterations

            Log.i(
                TAG,
                "conversion ${width}x$height packed: fast=${fastNanos / 1000.0}us " +
                    "legacy=${legacyNanos / 1000.0}us " +
                    "saved=${(legacyNanos - fastNanos) / 1000.0}us/frame",
            )
        }
    }

    /** The pre-fix guard, reproduced so the benchmark compares like for like. */
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

    private companion object {
        const val TAG = "KikCodeScanSweep"
        const val REMOTE_PAYLOAD_BYTES = 20
    }
}
