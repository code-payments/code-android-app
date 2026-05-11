package com.getcode.ui.scanner.internal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.compose.ui.geometry.Offset
import java.util.concurrent.TimeUnit
import kotlin.math.pow

internal class CameraGestureController(
    context: Context,
    private val gesturesEnabled: Boolean,
    private val cameraControl: CameraControl,
    private val cameraInfo: CameraInfo,
    private val onPinchStateChanged: (isPinching: Boolean, zoomRatio: Float) -> Unit = { _, _ -> },
    onTap: (Offset) -> MeteringPoint,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var initialZoomRatio = 0f
    private var initialZoomLevel = -1f
    private var gestureZoomFactor = 1f
    private var cumulativeScale = 1f
    private var appliedZoom = 1f
    private var isPinching = false

    private val maxZoom: Float
        get() = maxZoomOrNull ?: 1f
    private val minZoom: Float
        get() = minZoomOrNull ?: 1f

    private val maxZoomOrNull: Float?
        get() = cameraInfo.zoomState.value?.maxZoomRatio

    private val minZoomOrNull: Float?
        get() = cameraInfo.zoomState.value?.minZoomRatio

    private val currentZoom: Float
        get() = cameraInfo.zoomState.value?.zoomRatio ?: 1f

    // Pinch-to-zoom gesture detector
    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isPinching = true
                gestureZoomFactor = currentZoom
                appliedZoom = currentZoom
                cumulativeScale = 1f
                onPinchStateChanged(true, currentZoom)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cumulativeScale *= detector.scaleFactor
                val amplified = cumulativeScale.toDouble().pow(1.3).toFloat()
                val targetZoom = (gestureZoomFactor * amplified).coerceIn(
                    minZoom,
                    minOf(maxZoom, 20f)
                )
                // Lerp toward target to smooth lens-switch transitions
                appliedZoom += (targetZoom - appliedZoom) * 0.4f
                cameraControl.setZoomRatio(appliedZoom)
                onPinchStateChanged(true, appliedZoom)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                initialZoomRatio = currentZoom
                cumulativeScale = 1f
            }
        })

    // Gesture detector for tap-to-focus
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent): Boolean {
                initialZoomRatio = currentZoom
                return true
            }

            override fun onSingleTapUp(event: MotionEvent): Boolean {
                val point = onTap(Offset(event.x, event.y))
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
            ): Boolean = false

            override fun onShowPress(e: MotionEvent) {}
            override fun onLongPress(e: MotionEvent) {}
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean = false
        }
    )

    fun onTouchEvent(event: MotionEvent) {
        if (gesturesEnabled) {
            if (initialZoomLevel == -1f) {
                initialZoomLevel = cameraInfo.zoomState.value?.linearZoom ?: 0f
            }

            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_UP) {
                if (isPinching) {
                    onPinchStateChanged(false, currentZoom)
                    animateZoomReset(cameraInfo, cameraControl)
                    initialZoomRatio = currentZoom
                    isPinching = false
                }
            }
        }
    }

    private fun animateZoomReset(cameraInfo: CameraInfo?, cameraControl: CameraControl?) {
        val durationMs = 300L
        val frameInterval = 16L
        val maxSteps = durationMs / frameInterval
        val currentZoomLevel = cameraInfo?.zoomState?.value?.linearZoom ?: 0f

        val decrement = currentZoomLevel / maxSteps

        var currentStep = 0L
        handler.post(object : Runnable {
            override fun run() {
                if (currentStep < maxSteps) {
                    val newZoomLevel = currentZoomLevel - (decrement * currentStep)
                    cameraControl?.setLinearZoom(newZoomLevel.coerceIn(initialZoomLevel, 1f))
                    currentStep++
                    handler.postDelayed(this, frameInterval)
                } else {
                    cameraControl?.setLinearZoom(0f)
                }
            }
        })
    }
}