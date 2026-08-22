package com.kik.scan

import android.Manifest
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Which analysis resolution the device actually hands the scanner.
 *
 * `CodeScanner` asks for 1920x1080 through the deprecated `setTargetResolution`, which is a
 * *request*: CameraX resolves it against the supported sizes, the target rotation, and the
 * surface-combination limits of whatever else is bound. What comes back decides scan range, because
 * the native detector's minimum feature sizes are all normalised to `MIN(rows, cols) / 480` -- so
 * asking for 1080p and getting less tightens every threshold in angular terms.
 *
 * This binds the same use cases the app binds and records the negotiated size rather than the
 * requested one, then does the same for the candidate replacements so the fix can be chosen from
 * measurements instead of from the documentation.
 */
@RunWith(AndroidJUnit4::class)
class AnalysisResolutionTest {

    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    /** Minimal lifecycle owner parked in RESUMED, so `bindToLifecycle` opens the camera. */
    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun resume() = registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun destroy() = registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private val selector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .build()

    /**
     * Binds [analysis] alongside a preview, waits for a real frame, and logs what arrived.
     * Returns the size the camera actually delivered, or null if no frame arrived in time.
     */
    private fun measure(label: String, analysis: ImageAnalysis): Size? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)
        val preview = Preview.Builder().build()
        val owner = TestLifecycleOwner()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val executor = Executors.newSingleThreadExecutor()

        instrumentation.runOnMainSync {
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, preview, analysis)
            owner.resume()
        }

        try {
            val frame = CountDownLatch(1)
            var frameSize: Size? = null
            var rowStride = -1
            var pixelStride = -1
            analysis.setAnalyzer(executor) { image ->
                if (frameSize == null) {
                    frameSize = Size(image.width, image.height)
                    rowStride = image.planes[0].rowStride
                    pixelStride = image.planes[0].pixelStride
                    frame.countDown()
                }
                image.close()
            }
            val gotFrame = frame.await(15, TimeUnit.SECONDS)
            val size = frameSize ?: analysis.resolutionInfo?.resolution

            val detail = if (size != null) {
                val shortSide = minOf(size.width, size.height)
                val scalingRate = shortSide / 480.0
                val minArea = 220 * scalingRate
                val discPx = 2 * sqrt(minArea / Math.PI)
                // The centre disc is INNER_RING_RATIO (0.32) of the whole code graphic.
                val codePx = discPx / 0.32
                " scaling_rate=${"%.2f".format(scalingRate)}" +
                    " minCentreDisc=${"%.1f".format(discPx)}px" +
                    " impliedMinCode=${"%.0f".format(codePx)}px" +
                    " (${"%.1f".format(codePx / size.width.toDouble() * 100)}% of frame width)"
            } else {
                " <no frame>"
            }

            Log.i(
                TAG,
                "$label -> negotiated=${analysis.resolutionInfo?.resolution} frame=$size " +
                    "rowStride=$rowStride pixelStride=$pixelStride packed=${rowStride == size?.width} " +
                    "gotFrame=$gotFrame preview=${preview.resolutionInfo?.resolution}$detail",
            )
            return size
        } finally {
            instrumentation.runOnMainSync {
                analysis.clearAnalyzer()
                provider.unbindAll()
                owner.destroy()
            }
            executor.shutdown()
        }
    }

    /** The analysis configuration `CodeScanner` builds. Kept in step with it by hand. */
    private fun shippingAnalysis(): ImageAnalysis {
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1920, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
            )
            .build()

        return ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
    }

    /**
     * The scanner must be handed a full-width 16:9 stream, and this is the only place that is
     * checked.
     *
     * The bug this guards against is invisible by inspection: the code asked for 1920x1080 for
     * years and got a 720x720 square, because `setTargetResolution` is a hint the camera is free to
     * resolve however it likes. Nothing in the source read wrong -- the request and the reality
     * simply differed, and the only symptom was that scanning felt short-ranged.
     *
     * Both halves of the assertion matter. The short side sets how small a code can be and still
     * clear the detector's thresholds; the aspect ratio decides how much of the lens's field of
     * view is in the frame at all, since a square crop off a 4:3 sensor silently discards a quarter
     * of the width.
     */
    @Test
    fun shippingConfigurationGetsFullWidthSixteenByNine() {
        val negotiated = measure("shipping (ResolutionSelector 16:9 1080p)", shippingAnalysis())

        assertNotNull(negotiated, "camera delivered no frame")
        val shortSide = minOf(negotiated.width, negotiated.height)
        val aspect = maxOf(negotiated.width, negotiated.height).toDouble() / shortSide

        assertTrue(
            shortSide >= 1080,
            "analysis stream is $negotiated -- short side $shortSide is below 1080, which raises " +
                "the smallest decodable code and costs scan range",
        )
        assertTrue(
            aspect >= 1.6,
            "analysis stream is $negotiated -- aspect ratio ${"%.2f".format(aspect)} is narrower " +
                "than 16:9, so the frame is cropped in from the sensor's full width and the " +
                "scanner cannot see the edges of the lens's field of view",
        )
    }

    /**
     * Not a guard -- a record of what the alternatives negotiate on this device, so the choice
     * above can be re-checked rather than taken on faith. Logs only; a device that resolves these
     * differently is informative, not broken.
     */
    @Test
    fun alternativeConfigurationsForComparison() {
        measure(
            "deprecated setTargetResolution(1920x1080)",
            ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build(),
        )

        // The same request with the rotation pinned, to rule out the target-rotation frame as the
        // reason the deprecated path lands where it does.
        measure(
            "deprecated setTargetResolution(1920x1080)+ROTATION_0",
            ImageAnalysis.Builder()
                .setTargetResolution(Size(1920, 1080))
                .setTargetRotation(Surface.ROTATION_0)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build(),
        )

        // Above the chosen resolution. Reachable on this device, but the range sweep in
        // KikCodeRangeTest shows it decodes no smaller an angular target while costing four times
        // the per-frame work.
        val fourK = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(3840, 2160),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
            )
            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            .build()
        measure(
            "ResolutionSelector 16:9 2160p",
            ImageAnalysis.Builder()
                .setResolutionSelector(fourK)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build(),
        )
    }

    private companion object {
        const val TAG = "KikCodeRange"
    }
}
