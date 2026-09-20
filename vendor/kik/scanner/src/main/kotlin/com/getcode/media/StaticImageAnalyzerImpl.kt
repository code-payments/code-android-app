package com.getcode.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.getcode.codes.kikcode.LuminancePlane
import com.getcode.codes.kikcode.StillImageCodeSearch
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.models.ScannableKikCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class StaticImageAnalyzerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: KikCodeScanner,
) : StaticImageAnalyzer {

    override suspend fun analyze(uri: Uri): StaticImageResult = withContext(Dispatchers.Default) {
        val bitmap = decode(uri) ?: return@withContext StaticImageResult.NotFound

        try {
            search(bitmap, BUDGET)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun search(bitmap: Bitmap, budget: Duration): StaticImageResult {
        val started = TimeSource.Monotonic.markNow()

        StillImageCodeSearch.candidates(bitmap.width, bitmap.height).forEach { candidate ->
            currentCoroutineContext().ensureActive()

            if (started.elapsedNow() > budget) return StaticImageResult.Exhausted

            val scanned = scan(bitmap, candidate)
            if (scanned != null) return StaticImageResult.Found(scanned)
        }

        return StaticImageResult.NotFound
    }

    private suspend fun scan(
        bitmap: Bitmap,
        candidate: StillImageCodeSearch.Candidate,
    ): ScannableKikCode? {
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

        // `createBitmap` and `createScaledBitmap` both return the source unchanged when nothing
        // needs doing, so recycling unconditionally would free the caller's bitmap.
        if (crop != bitmap) crop.recycle()
        if (scaled != crop && scaled != bitmap) scaled.recycle()

        val luminance = LuminancePlane.fromArgb(pixels, width, height)

        return scanner.scanKikCode(luminance, width, height).getOrNull()
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false

            // A modern phone photo is 4000px across, and the ladder's cost is quadratic in that.
            // Downsampling first costs nothing the scanner can see -- a code too small to survive
            // this is already too small for the deepest zoom to recover.
            val longestSide = maxOf(info.size.width, info.size.height)
            if (longestSide > MAX_SOURCE_SIDE) {
                val sample = longestSide / MAX_SOURCE_SIDE
                decoder.setTargetSampleSize(Integer.highestOneBit(sample))
            }
        }
    }.getOrNull()

    private companion object {
        /**
         * How long the whole search may take. Long enough that no successful search has ever hit
         * it, short enough that a photo of a wall gives up. A guess, not a measurement -- see the
         * spec's open decisions.
         */
        val BUDGET: Duration = 8.seconds

        const val MAX_SOURCE_SIDE = 2400
    }
}
