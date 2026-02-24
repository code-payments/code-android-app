package com.getcode.libs.code.detection

import androidx.camera.core.ImageProxy

interface CodeScanResult {

    data class QrCode(
        val results: List<String>,
    ) : CodeScanResult
}

interface CodeDetector<T> {
    suspend fun detect(image: ImageProxy): CodeScanResult?
}