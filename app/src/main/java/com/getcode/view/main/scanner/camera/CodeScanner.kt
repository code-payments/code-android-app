package com.getcode.view.main.scanner.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import com.getcode.LocalBiometricsState
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.utils.AnimationUtils
import com.getcode.util.Biometrics
import com.getcode.utils.trace
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import com.kik.kikx.kikcodes.implementation.rememberKikCodeAnalyzer
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors

@Composable
fun CodeScanner(
    scanningEnabled: Boolean,
    cameraGesturesEnabled: Boolean,
    invertedDragZoomEnabled: Boolean,
    onPreviewStateChanged: (Boolean) -> Unit,
    onCodeScanned: (ScannableKikCode) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scanner = remember { KikCodeScannerImpl() }

    val cameraController = remember { LifecycleCameraController(context) }

    val previewView = remember(context, cameraController) {
        PreviewView(context).apply { controller = cameraController }
    }

    val preview = remember {
        Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
    }

    val cameraSelector = remember {
        val lensFacing = CameraSelector.LENS_FACING_BACK
        CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var autoFocusPoint by remember { mutableStateOf(Offset.Unspecified) }
    var gestureController by remember { mutableStateOf<CameraGestureController?>(null) }

    val kikCodeAnalyzer = rememberKikCodeAnalyzer(context, scanner, onCodeScanned)

    val biometricsState = LocalBiometricsState.current

    val scope = rememberCoroutineScope()
    LaunchedEffect(scanner, biometricsState.isAwaitingAuthentication, Biometrics.promptActive) {
        val active = Biometrics.promptActive || biometricsState.isAwaitingAuthentication
        val cameraProvider = context.getCameraProvider()
        if (!active) {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } else {
            cameraProvider.unbindAll()
        }
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            scope.launch {
                val cameraProvider = context.getCameraProvider()
                cameraProvider.unbindAll()
                camera = null
            }
        } else if (event == Lifecycle.Event.ON_RESUME) {
            scope.launch {
                if (camera == null) {
                    if (!biometricsState.isAwaitingAuthentication) {
                        val cameraProvider = context.getCameraProvider()
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(camera, cameraGesturesEnabled, invertedDragZoomEnabled) {
        camera?.let {
            gestureController = CameraGestureController(
                context = context,
                cameraControl = it.cameraControl,
                cameraInfo = it.cameraInfo,
                gesturesEnabled = cameraGesturesEnabled,
                invertedDragEnabled = invertedDragZoomEnabled
            ) { touchedAt ->
                autoFocusPoint = touchedAt
                previewView.meteringPointFactory.createPoint(touchedAt.x, touchedAt.y)
            }
        }
    }

    LaunchedEffect(previewView) {
        previewView.setOnTouchListener { _, event ->
            gestureController?.onTouchEvent(event)
            true
        }
    }

    var streamState by remember(previewView) {
        mutableStateOf(PreviewView.StreamState.IDLE)
    }

    LaunchedEffect(streamState) {
        if (streamState == PreviewView.StreamState.STREAMING) {
            trace("camera ready")
        }
    }

    LaunchedEffect(streamState, scanningEnabled) {
        if (streamState == PreviewView.StreamState.STREAMING && scanningEnabled) {
            imageAnalysis.setAnalyzer(cameraExecutor, kikCodeAnalyzer)
        } else {
            imageAnalysis.clearAnalyzer()
        }
    }

    LaunchedEffect(previewView) {
        previewView.previewStreamState.asFlow()
            .distinctUntilChanged()
            .onEach { Timber.d(it.name) }
            .onEach { streamState = it }
            .onEach {
                val streaming = it == PreviewView.StreamState.STREAMING
                onPreviewStateChanged(streaming)
            }.launchIn(this)
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    FocusIndicator(autoFocusPoint) {
        autoFocusPoint = Offset.Unspecified
    }

    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = streamState != PreviewView.StreamState.STREAMING,
        enter = fadeIn(tween(AnimationUtils.animationTime / 2)),
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