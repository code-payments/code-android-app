package com.kik.kikx.kikcodes.implementation

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.getcode.util.toByteArray
import com.getcode.utils.ErrorUtils
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KikCodeAnalyzer(
    private val scanner: KikCodeScanner,
    private val onCodeScanned: (ScannableKikCode) -> Unit,
): ImageAnalysis.Analyzer, CoroutineScope by CoroutineScope(Dispatchers.IO) {

    override fun analyze(imageProxy: ImageProxy) {
        launch {
            scanner.scanKikCode(
                imageProxy.toByteArray(),
                imageProxy.width,
                imageProxy.height,
            ).onSuccess { result ->
                onCodeScanned(result)
                imageProxy.close()

            }.onFailure { error ->
                when (error) {
                    is KikCodeScanner.NoKikCodeFoundException -> Unit
                    else -> ErrorUtils.handleError(error)
                }
                imageProxy.close()
            }
        }
    }
}