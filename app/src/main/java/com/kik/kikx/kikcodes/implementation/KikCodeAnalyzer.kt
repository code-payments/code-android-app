package com.kik.kikx.kikcodes.implementation

import android.R.attr.bitmap
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.getcode.util.toByteArray
import com.getcode.util.uriToBitmap
import com.getcode.utils.ErrorUtils
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.models.ScannableKikCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import javax.inject.Inject


@Composable
fun rememberKikCodeAnalyzer(
    context: Context,
    scanner: KikCodeScanner,
    onCodeScanned: (ScannableKikCode) -> Unit
): KikCodeAnalyzer {
    return remember(context, scanner, onCodeScanned) {
        KikCodeAnalyzer(context, scanner).apply {
            this.onCodeScanned = onCodeScanned
        }
    }
}

class KikCodeAnalyzer @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val scanner: KikCodeScanner,
) : ImageAnalysis.Analyzer, CoroutineScope by CoroutineScope(Dispatchers.IO) {

    var onCodeScanned: (ScannableKikCode) -> Unit = { }
    var onNoCodeFound: () -> Unit = { }

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
            val bitmap = context.uriToBitmap(uri)
            if (bitmap != null) {
                scanner.scanKikCode(
                    bitmap.toByteArray(),
                    bitmap.width,
                    bitmap.height,
                ).onSuccess { result ->
                    onCodeScanned(result)
                    bitmap.recycle()

                }.onFailure { error ->
                    when (error) {
                        is KikCodeScanner.NoKikCodeFoundException -> onNoCodeFound()
                        else -> ErrorUtils.handleError(error)
                    }
                    bitmap.recycle()
                }
            }
        }
    }
}