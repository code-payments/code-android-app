package com.getcode.view.main.home.components

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.asFlow
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.startupLog
import com.getcode.ui.utils.AnimationUtils
import com.getcode.util.toByteArray
import com.getcode.utils.ErrorUtils
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors

@Composable
fun CodeScanner(
    onPreviewStateChanged: (Boolean) -> Unit,
    onCodeScanned: (ScannableKikCode) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scanner = remember { KikCodeScannerImpl() }
    val previewView = remember(context) { PreviewView(context) }

    val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build()

    LaunchedEffect(scanner) {
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()

        val lensFacing = CameraSelector.LENS_FACING_BACK
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
    }

    var streamState by remember(previewView) {
        mutableStateOf(PreviewView.StreamState.IDLE)
    }

    LaunchedEffect(streamState) {
        if (streamState == PreviewView.StreamState.STREAMING) {
            startupLog("camera ready")
        }
    }

    LaunchedEffect(previewView) {
        val scope = this
        previewView.previewStreamState.asFlow()
            .distinctUntilChanged()
            .onEach { Timber.d(it.name) }
            .onEach { streamState = it }
            .map {
                it.also {
                    val streaming = it == PreviewView.StreamState.STREAMING
                    onPreviewStateChanged(streaming)
                } == PreviewView.StreamState.STREAMING
            }
            .onEach { streaming ->
                if (streaming) {
                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        scope.launch(Dispatchers.IO) {
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
                } else {
                    imageAnalysis.clearAnalyzer()
                }

            }.launchIn(this)
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = streamState != PreviewView.StreamState.STREAMING,
        enter = fadeIn(
            animationSpec = tween(AnimationUtils.animationTime / 2)
        ),
        exit = fadeOut(tween(AnimationUtils.animationTime / 2))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.background)
        )
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider {
    return withContext(Dispatchers.IO) {
        ProcessCameraProvider.getInstance(this@getCameraProvider).get()
    }
}