package com.getcode.media

import android.net.Uri
import com.kik.kikx.models.ScannableKikCode

/** What a still-image search ended up finding. */
sealed interface StaticImageResult {
    /**
     * [tier] and [zoom] are the crop that decoded, reported so the caller can say how deep the
     * ladder had to go. The ladder's constants are inherited and unmeasured, and this is the
     * only place the depth a real image needed is visible.
     */
    data class Found(
        val code: ScannableKikCode,
        val tier: Int,
        val zoom: Float,
    ) : StaticImageResult

    /** The ladder was walked to the end and nothing decoded. */
    data object NotFound : StaticImageResult

    /** The budget expired or the caller cancelled. Not the same as [NotFound]. */
    data object Exhausted : StaticImageResult
}

/**
 * Searches a picked or shared image for a Kik code.
 *
 * Implemented by [StaticImageAnalyzerImpl]; kept as an interface so `androidTest` can drive the
 * search with synthesized bitmaps and no content resolver.
 */
interface StaticImageAnalyzer {
    suspend fun analyze(uri: Uri): StaticImageResult
}
