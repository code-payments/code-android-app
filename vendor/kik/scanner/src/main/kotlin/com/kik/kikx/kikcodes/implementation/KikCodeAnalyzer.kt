package com.kik.kikx.kikcodes.implementation

import android.net.Uri
import androidx.camera.core.ImageProxy
import com.getcode.libs.code.detection.CodeDetector
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.libs.code.detection.NativeScanSample
import com.getcode.media.StaticImageAnalyzer
import com.getcode.util.toByteArray
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.kikcodes.ScannerError
import com.kik.kikx.models.ScannableKikCode
import javax.inject.Inject

data class KikCodeResult(
    val kikCode: ScannableKikCode,
    override val nativeScan: NativeScanSample? = null,
) : CodeScanResult


class KikCodeAnalyzer @Inject constructor(
    private val scanner: KikCodeScanner,
) : CodeDetector<ScannableKikCode> {

    @Inject
    lateinit var staticImageAnalyzer: StaticImageAnalyzer

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

    suspend fun detect(uri: Uri): CodeScanResult? {
        return staticImageAnalyzer.analyze(uri)
            .map { KikCodeResult(it) }
            .getOrElse { error ->
                if (error !is ScannerError) throw error
                null
            }
    }
}