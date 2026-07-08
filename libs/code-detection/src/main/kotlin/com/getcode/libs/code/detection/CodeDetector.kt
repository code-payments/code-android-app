package com.getcode.libs.code.detection

import androidx.camera.core.ImageProxy

interface CodeScanResult {

    /** Native-scan timing for the frame that produced this result, if measured. */
    val nativeScan: NativeScanSample? get() = null

    data class QrCode(
        val results: List<String>,
        override val nativeScan: NativeScanSample? = null,
    ) : CodeScanResult
}

interface CodeDetector<T> {
    suspend fun detect(image: ImageProxy): CodeScanResult?
}