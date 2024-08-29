package com.getcode.view.main.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import com.kik.kikx.kikcodes.implementation.KikCodeAnalyzer
import com.kik.kikx.kikcodes.implementation.KikCodeScannerImpl
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Composable
fun CodeScanner(
    scanningEnabled: Boolean,
    cameraGesturesEnabled: Boolean,
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

    val kikCodeAnalyzer = remember(scanner, onCodeScanned) {
        KikCodeAnalyzer(scanner, onCodeScanned)
    }

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

    LaunchedEffect(camera, cameraGesturesEnabled) {
        camera?.let {
            val cameraControl = it.cameraControl
            val cameraInfo = it.cameraInfo

            setupInteractionControls(
                previewView,
                cameraControl,
                cameraInfo,
                cameraGesturesEnabled,
            ) { point ->
                autoFocusPoint = point
            }
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

private fun setupInteractionControls(
    previewView: PreviewView,
    cameraControl: CameraControl,
    cameraInfo: CameraInfo,
    cameraGesturesEnabled: Boolean,
    onTap: (Offset) -> Unit,
) {
    var shouldIgnoreScroll = false
    val handler = Handler(Looper.getMainLooper())
    var resetIgnore: Runnable? = null
    var initialZoomLevel = 0f
    var accumulatedDelta = 0f
    // Pinch-to-zoom gesture detector
    val scaleGestureDetector = ScaleGestureDetector(
        previewView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                shouldIgnoreScroll = true
                resetIgnore?.let { handler.removeCallbacks(it) }
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                val newZoomRatio = currentZoomRatio * delta

                // Clamp the new zoom ratio between the minimum and maximum zoom ratio
                val clampedZoomRatio = newZoomRatio.coerceIn(
                    cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
                    cameraInfo.zoomState.value?.maxZoomRatio ?: currentZoomRatio
                )

                // Apply the zoom to the camera control
                cameraControl.setZoomRatio(clampedZoomRatio)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                initialZoomLevel = cameraInfo.zoomState.value?.zoomRatio ?: 1f
                resetIgnore = Runnable { shouldIgnoreScroll = false }
                previewView.postDelayed(resetIgnore, 500)
            }
        })

    // Gesture detector for tap and drag-to-zoom
    val gestureDetector = GestureDetector(
        previewView.context,
        object : GestureDetector.OnGestureListener {


            override fun onDown(e: MotionEvent): Boolean {
                initialZoomLevel = cameraInfo.zoomState.value?.zoomRatio ?: 1f
                accumulatedDelta = 0f
                return true
            }

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                onTap(Offset(event.x, event.y))

                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(5, TimeUnit.SECONDS)
                    .build()

                cameraControl.startFocusAndMetering(action)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (!shouldIgnoreScroll) {
                    accumulatedDelta += distanceY

                    val deltaZoom = accumulatedDelta / 1000f
                    val maxZoom = cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                    val minZoom = cameraInfo.zoomState.value?.minZoomRatio ?: 1f

                    val newZoom = (initialZoomLevel + deltaZoom).coerceIn(minZoom, maxZoom)
                    cameraControl.setZoomRatio(newZoom)
                }
                return true
            }

            override fun onShowPress(e: MotionEvent) {}
            override fun onLongPress(e: MotionEvent) {}
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                return false
            }
        })

    previewView.setOnTouchListener { _, event ->
        if (cameraGesturesEnabled) {
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_UP) {
                animateZoomReset(cameraInfo, cameraControl)
                initialZoomLevel = cameraInfo.zoomState.value?.zoomRatio ?: 1f
            }
        }
        true
    }
}

private fun animateZoomReset(cameraInfo: CameraInfo, cameraControl: CameraControl) {
    val handler = Handler(Looper.getMainLooper())
    val durationMs = 300L
    val frameInterval = 16L
    val maxSteps = durationMs / frameInterval
    val currentZoomLevel = cameraInfo.zoomState.value?.linearZoom ?: 0f

    val decrement = currentZoomLevel / maxSteps

    var currentStep = 0L
    handler.post(object : Runnable {
        override fun run() {
            if (currentStep < maxSteps) {
                val newZoomLevel = currentZoomLevel - (decrement * currentStep)
                cameraControl.setLinearZoom(newZoomLevel.coerceIn(0f, 1f))
                currentStep++
                handler.postDelayed(this, frameInterval)
            } else {
                cameraControl.setLinearZoom(0f)
            }
        }
    })
}
