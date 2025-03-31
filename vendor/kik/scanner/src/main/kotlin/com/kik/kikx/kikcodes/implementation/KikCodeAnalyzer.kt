package com.kik.kikx.kikcodes.implementation

import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.getcode.media.StaticImageAnalyzer
import com.getcode.util.toByteArray
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.kikcodes.ScannerError
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject


@Composable
fun rememberKikCodeAnalyzer(
    scanner: KikCodeScanner,
    onError: (Throwable) -> Unit = { },
    onCodeScanned: (ScannableKikCode) -> Unit,
): KikCodeAnalyzer {
    return remember(scanner, onCodeScanned, onError) {
        KikCodeAnalyzer(scanner).apply {
            this.onCodeScanned = onCodeScanned
            this.onError = onError
        }
    }
}

class KikCodeAnalyzer @Inject constructor(
    private val scanner: KikCodeScanner,
) : ImageAnalysis.Analyzer, CoroutineScope by CoroutineScope(Dispatchers.IO) {

    @Inject
    lateinit var staticImageAnalyzer: StaticImageAnalyzer

    var onCodeScanned: (ScannableKikCode) -> Unit = { }
    var onNoCodeFound: () -> Unit = { }
    var onError: (Throwable) -> Unit = { }

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
                    is ScannerError -> Unit
                    else -> {
                        onError(error)
                    }
                }
                imageProxy.close()
            }
        }
    }

    fun analyze(uri: Uri) {
        launch {
            staticImageAnalyzer.analyze(uri)
                .onSuccess { result ->
                    onCodeScanned(result)
                }.onFailure { error ->
                    when (error) {
                        is ScannerError -> onNoCodeFound()
                        else -> onError(error)
                    }
                }
        }
    }
}