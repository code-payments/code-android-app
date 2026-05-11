package com.getcode.ui.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.camera.core.Camera
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.getcode.libs.biometrics.Biometrics
import com.getcode.libs.code.detection.CodeScanResult
import com.getcode.theme.CodeTheme
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.scanner.internal.CameraGestureController
import com.getcode.ui.scanner.internal.FocusIndicator
import com.getcode.ui.scanner.processing.rememberMultiCodeAnalyzer
import com.getcode.ui.utils.AnimationUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CodeScanner(
    scanningEnabled: Boolean,
    cameraGesturesEnabled: Boolean,
    modifier: Modifier = Modifier,
    onPinchStateChanged: (Boolean, Float) -> Unit,
    onPreviewStateChanged: (Boolean) -> Unit,
    onCodeScanned: (CodeScanResult) -> Unit,
    onError: (Throwable) -> Unit = { },
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val preview = remember {
        Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
    }

    val cameraSelector = remember {
        val lensFacing = CameraSelector.LENS_FACING_BACK
        CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetResolution(Size(1920, 1080))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var autoFocusPoint by remember { mutableStateOf(Offset.Unspecified) }
    var gestureController by remember { mutableStateOf<CameraGestureController?>(null) }
    var isPinching by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    val codeAnalyzer = rememberMultiCodeAnalyzer(
        onCodeScanned = onCodeScanned,
        onError = onError
    )

    val biometricsState = LocalBiometricsState.current

    val scope = rememberCoroutineScope()
    LaunchedEffect(biometricsState.isAwaitingAuthentication, Biometrics.promptActive) {
        val active = Biometrics.promptActive || biometricsState.isAwaitingAuthentication
        val cameraProvider = context.getCameraProvider()
        if (!active) {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycleSafely(
                context,
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            ).onSuccess {
                camera = it
            }.onFailure { onError(it) }
        } else {
            cameraProvider.unbindAll()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Synchronously unbind camera before the PreviewView leaves composition
            // to prevent RenderNode animator from firing on a detached surface.
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            camera = null
        }
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            camera = null
        } else if (event == Lifecycle.Event.ON_RESUME) {
            scope.launch {
                if (camera == null) {
                    if (!biometricsState.isAwaitingAuthentication) {
                        val cameraProvider = context.getCameraProvider()
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycleSafely(
                            context,
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        ).onSuccess {
                            camera = it
                        }.onFailure { onError(it) }
                    }
                }
            }
        }
    }

    LaunchedEffect(camera, cameraGesturesEnabled) {
        camera?.let {
            gestureController = CameraGestureController(
                context = context,
                cameraControl = it.cameraControl,
                cameraInfo = it.cameraInfo,
                gesturesEnabled = cameraGesturesEnabled,
                onPinchStateChanged = onPinchStateChanged,
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

    LaunchedEffect(streamState, scanningEnabled, codeAnalyzer) {
        if (streamState == PreviewView.StreamState.STREAMING && scanningEnabled) {
            imageAnalysis.setAnalyzer(cameraExecutor, codeAnalyzer)
        } else {
            imageAnalysis.clearAnalyzer()
        }
    }

    LaunchedEffect(previewView) {
        previewView.previewStreamState.asFlow()
            .distinctUntilChanged()
            .onEach { trace(tag = "CodeScanner", message = it.name, type = TraceType.Silent) }
            .onEach { streamState = it }
            .onEach {
                val streaming = it == PreviewView.StreamState.STREAMING
                onPreviewStateChanged(streaming)
            }.launchIn(this)
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())

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

private suspend fun ProcessCameraProvider.bindToLifecycleSafely(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraSelector: CameraSelector,
    preview: Preview,
    imageAnalysis: ImageAnalysis,
    retries: Int = 3,
): Result<Camera> {
    val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    if (!hasCamera) {
        return Result.failure(IllegalStateException("No camera available on this device"))
    }

    return runCatching {
        bindWithRetry(retries) {
            bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        }
    }
}

suspend fun bindWithRetry(
    retries: Int = 3,
    bind: suspend () -> Camera
): Camera {
    repeat(retries) { attempt ->
        try {
            return bind()
        } catch (e: Exception) {
            if (attempt == retries - 1) throw e
            delay(1000)
        }
    }
    throw NoCamerasAvailableException()
}

class NoCamerasAvailableException : Throwable()