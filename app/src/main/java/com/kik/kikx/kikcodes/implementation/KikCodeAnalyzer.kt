package com.kik.kikx.kikcodes.implementation

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.getcode.media.MediaScanner
import com.getcode.util.toByteArray
import com.getcode.utils.ErrorUtils
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.models.ScannableKikCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject


@Composable
fun rememberKikCodeAnalyzer(
    scanner: KikCodeScanner,
    onCodeScanned: (ScannableKikCode) -> Unit
): KikCodeAnalyzer {
    return remember(scanner, onCodeScanned) {
        KikCodeAnalyzer(scanner).apply {
            this.onCodeScanned = onCodeScanned
        }
    }
}

class KikCodeAnalyzer @Inject constructor(
    private val scanner: KikCodeScanner,
) : ImageAnalysis.Analyzer, CoroutineScope by CoroutineScope(Dispatchers.IO) {

    @Inject
    lateinit var staticImageHelper: StaticImageHelper

    var onCodeScanned: (ScannableKikCode) -> Unit = { }
    var onNoCodeFound: () -> Unit = { }

    @Inject
    internal lateinit var mediaScanner: MediaScanner

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

    fun analyze(uri: Uri) {
        launch {
            staticImageHelper.analyze(uri)
                .onSuccess { result ->
                    onCodeScanned(result)
                }.onFailure { error ->
                    when (error) {
                        is KikCodeScanner.NoKikCodeFoundException -> onNoCodeFound()
                        else -> ErrorUtils.handleError(error)
                    }
                }
        }
    }
}