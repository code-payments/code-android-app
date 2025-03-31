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
import com.getcode.ui.utils.AnimationUtils
import java.util.concurrent.TimeUnit

internal class CameraGestureController(
    context: Context,
    invertedDragEnabled: Boolean,
    private val gesturesEnabled: Boolean,
    private val cameraControl: CameraControl,
    private val cameraInfo: CameraInfo,
    onTap: (Offset) -> MeteringPoint,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var shouldIgnoreScroll = false
    private var resetIgnore: Runnable? = null
    private var initialZoomRatio = 0f
    private var initialZoomLevel = -1f
    private var accumulatedDelta = 0f

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
                shouldIgnoreScroll = true
                resetIgnore?.let { handler.removeCallbacks(it) }
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val delta = detector.scaleFactor
                val newZoomRatio = currentZoom * delta

                // Clamp the new zoom ratio between the minimum and maximum zoom ratio
                val clampedZoomRatio = newZoomRatio.coerceIn(
                    minZoom,
                    maxZoomOrNull ?: currentZoom
                )

                // Apply the zoom to the camera control
                cameraControl.setZoomRatio(clampedZoomRatio)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                initialZoomRatio = currentZoom
                resetIgnore = Runnable { shouldIgnoreScroll = false }
                resetIgnore?.let { handler.postDelayed(it, 500) }
            }
        })

    // Gesture detector for tap and drag-to-zoom
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent): Boolean {
                initialZoomRatio = currentZoom
                accumulatedDelta = 0f
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
            ): Boolean {
                if (!shouldIgnoreScroll) {
                    accumulatedDelta = if (invertedDragEnabled) {
                        accumulatedDelta + distanceY * 0.5f
                    } else {
                        accumulatedDelta - distanceY * 0.5f
                    }

                    val zoomDelta = AnimationUtils.ease(
                        value = accumulatedDelta,
                        fromRange = 0f..250f,
                        toRange = 0f..10f,
                        easeIn = true,
                        easeOut = false
                    )

                    val newZoom = (initialZoomRatio + zoomDelta).coerceIn(minZoom, maxZoom)
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
                animateZoomReset(cameraInfo, cameraControl)
                initialZoomRatio = currentZoom
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