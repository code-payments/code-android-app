package com.kik.kikx.kikcodes.implementation

import android.net.Uri
import androidx.camera.core.ImageProxy
import com.getcode.libs.code.detection.CodeDetector
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.media.StaticImageAnalyzer
import com.getcode.media.StaticImageResult
import com.getcode.util.toByteArray
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.kikcodes.ScannerError
import com.kik.kikx.models.ScannableKikCode
import javax.inject.Inject

data class KikCodeResult(val kikCode: ScannableKikCode) : CodeScanResult


class KikCodeAnalyzer @Inject constructor(
    private val scanner: KikCodeScanner,
    private val staticImageAnalyzer: StaticImageAnalyzer,
) : CodeDetector<ScannableKikCode> {

    override suspend fun detect(
        image: ImageProxy,
    ): CodeScanResult? {
        return scanner.scanKikCode(image.toByteArray(), image.width, image.height)
            .map { KikCodeResult(it) }
            .getOrElse { error ->
                if (error !is ScannerError) throw error
                null
            }
    }

    /**
     * The still-image path returns the analyzer's result unchanged: the caller has to tell
     * `Exhausted` from `NotFound` to say anything useful about a search that found nothing.
     */
    suspend fun detect(uri: Uri): StaticImageResult = staticImageAnalyzer.analyze(uri)
}