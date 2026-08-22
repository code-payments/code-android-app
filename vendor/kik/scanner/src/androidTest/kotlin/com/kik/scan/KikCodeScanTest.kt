package com.kik.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getcode.codes.kikcode.LuminancePlane
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
 * [LuminancePlane.unpad] conversion the camera analyzer uses, and handed to the native scanner.
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
        val converted = LuminancePlane.unpad(
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
                LuminancePlane.unpad(packed.data, width, height, packed.rowStride, 1) === packed.data,
                "${width}x$height packed frame should take the fast path",
            )
            assertTrue(
                LuminancePlane.unpad(padded.data, width, height, padded.rowStride, 1) !== padded.data,
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
                LuminancePlane.unpad(data, width, height, width, 1)
                legacyUnpad(data, width, height, width, 1)
            }

            val iterations = 30
            val legacyNanos = measureNanoTime {
                repeat(iterations) { legacyUnpad(data, width, height, width, 1) }
            } / iterations

            // The fast path returns in a few instructions, so 30 iterations sits at the
            // System.nanoTime measurement floor -- run it enough times to actually resolve.
            val fastIterations = 200_000
            val fastNanos = measureNanoTime {
                repeat(fastIterations) { LuminancePlane.unpad(data, width, height, width, 1) }
            }.toDouble() / fastIterations

            Log.i(
                TAG,
                "conversion ${width}x$height packed: fast=${fastNanos / 1000.0}us " +
                    "legacy=${legacyNanos / 1000.0}us " +
                    "saved=${legacyNanos / 1000.0 - fastNanos / 1000.0}us/frame",
            )
        }
    }

    /**
     * The shared packing rule lives in `:libs:codes:kikcode` so iOS applies the same one, which puts
     * a cross-module call on the hot path where there used to be a module-local function.
     *
     * Unoptimized, that hop is not free: in a debug build it costs a consistent ~2.4x a module-local
     * call (2.5ns on an emulator, 7.4ns on an S25 Ultra). R8 all but erases it -- the same A/B on a
     * minified release build measures 3.77ns shared vs 3.43ns local, a 0.34ns difference, because
     * the function is a two-int comparison and a return and gets inlined. Users run the optimized
     * build, so the honest figure for sharing the rule is ~0.3ns/frame.
     *
     * Either way this does NOT assert an absolute nanosecond budget: that would encode the speed of
     * whatever hardware and build type it last ran on, and flip-flops between them.
     *
     * What actually matters is that the fast path stays orders of magnitude below the copy it
     * replaces. A real regression -- someone making the packed case copy again -- moves it from
     * nanoseconds to milliseconds, a ~350,000x jump, not a few nanoseconds. So the gate is measured
     * against the legacy cost on the same device, and the A/B delta is logged as an observation.
     *
     * Both timings are taken over several alternating rounds keeping the best of each: a single
     * round is dominated by whichever loop the JIT compiled first, which is enough to invent a
     * double-digit-nanosecond "difference" that reverses if you swap the order.
     */
    @Test
    fun sharedFastPathStaysOrdersOfMagnitudeBelowTheCopyItReplaces() {
        val (width, height) = resolutions.last()
        val data = ByteArray(width * height) { (it % 251).toByte() }
        val iterations = 200_000

        repeat(50_000) {
            LuminancePlane.unpad(data, width, height, width, 1)
            localUnpad(data, width, height, width, 1)
        }

        fun timeShared(): Double = measureNanoTime {
            repeat(iterations) { LuminancePlane.unpad(data, width, height, width, 1) }
        }.toDouble() / iterations

        fun timeLocal(): Double = measureNanoTime {
            repeat(iterations) { localUnpad(data, width, height, width, 1) }
        }.toDouble() / iterations

        var shared = Double.MAX_VALUE
        var local = Double.MAX_VALUE
        repeat(5) { round ->
            // alternate which runs first so neither systematically pays for the other's warmup
            if (round % 2 == 0) {
                shared = minOf(shared, timeShared())
                local = minOf(local, timeLocal())
            } else {
                local = minOf(local, timeLocal())
                shared = minOf(shared, timeShared())
            }
        }

        // The copy the fast path exists to avoid, on this same device, as the yardstick.
        val legacyIterations = 30
        val legacy = measureNanoTime {
            repeat(legacyIterations) { legacyUnpad(data, width, height, width, 1) }
        }.toDouble() / legacyIterations

        Log.i(
            TAG,
            "fast path ${width}x$height: shared=${shared}ns local=${local}ns " +
                "delta=${shared - local}ns/frame legacy=${legacy / 1_000}us " +
                "ratio=1:${(legacy / shared).toLong()}",
        )

        // A fast path that stopped being one shows up as a four-to-five-order-of-magnitude move,
        // not a few nanoseconds. 1000x is far below the ~350,000x actually observed and far above
        // any plausible cross-module dispatch cost, on any device.
        assertTrue(
            shared * 1_000 < legacy,
            "shared fast path regressed: shared=${shared}ns is not <1/1000th of the " +
                "${legacy / 1_000}us copy it replaces (local baseline=${local}ns)",
        )
    }

    /**
     * What sustained scanning costs the collector.
     *
     * The wall-clock benchmarks above measure one frame in isolation, which says nothing about the
     * *shape* of the original complaint: scanning that is occasionally slow rather than uniformly
     * slow. A steady per-frame tax reads as the latter. Blocking GC reads as the former.
     *
     * The pre-fix path allocated twice per frame at 1080p — once to read the plane out of the
     * `ByteBuffer`, once more for the unpadding copy — roughly 4MB/frame, ~120MB/s at 30fps. The fix
     * removes the second. A reusable frame buffer would remove the first as well, which is the only
     * reason variant C is here: to size that remaining opportunity before anyone builds it.
     *
     * Reported as blocking GC count and time, since that is the part a user actually feels.
     *
     * Read the raw GC *counts* with care: the variants differ by two orders of magnitude in wall
     * time, which gives the concurrent collector correspondingly more opportunity to run during the
     * slow one. The quantity that compares cleanly across variants is allocations per frame — two,
     * one, none.
     */
    @Test
    fun sustainedScanningGcCost() {
        val (width, height) = resolutions.last()
        val frames = 300 // ten seconds of scanning at 30fps
        val bufferBytes = width * height // packed: the common case, and the one the fix targets

        fun gcStat(name: String): Long = Debug.getRuntimeStat(name)?.toLongOrNull() ?: -1L

        fun measure(label: String, frame: (Int) -> ByteArray) {
            Runtime.getRuntime().gc()
            Thread.sleep(SETTLE_MS)
            val gcBefore = gcStat("art.gc.gc-count")
            val blockingBefore = gcStat("art.gc.blocking-gc-count")
            val blockingTimeBefore = gcStat("art.gc.blocking-gc-time")

            var sink = 0L
            val elapsed = measureNanoTime {
                repeat(frames) { i -> sink += frame(i)[0].toLong() }
            }

            Log.i(
                TAG,
                "gc $label ${width}x$height over $frames frames: " +
                    "gc=${gcStat("art.gc.gc-count") - gcBefore} " +
                    "blockingGc=${gcStat("art.gc.blocking-gc-count") - blockingBefore} " +
                    "blockingGcTime=${gcStat("art.gc.blocking-gc-time") - blockingTimeBefore}ms " +
                    "wall=${elapsed / 1_000_000}ms sink=$sink",
            )
        }

        // Pre-fix: a fresh plane read plus the unpadding copy, every frame.
        measure("legacy") {
            legacyUnpad(ByteArray(bufferBytes), width, height, width, 1)
        }

        // Current: the plane read still allocates; the fast path adds nothing.
        measure("fastPath") {
            LuminancePlane.unpad(ByteArray(bufferBytes), width, height, width, 1)
        }

        // Hypothetical: ImageAnalysis delivers frames serially, so one buffer could be reused.
        val reusable = ByteArray(bufferBytes)
        measure("reusedBuffer") {
            LuminancePlane.unpad(reusable, width, height, width, 1)
        }
    }

    /** A module-local copy of the fast path, used only as the A/B baseline above. */
    private fun localUnpad(
        data: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
    ): ByteArray {
        if (rowStride == width && pixelStride == 1) return data
        val cleanData = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                cleanData[y * width + x] = data[y * rowStride + x * pixelStride]
            }
        }
        return cleanData
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
        const val SETTLE_MS = 200L
    }
}
