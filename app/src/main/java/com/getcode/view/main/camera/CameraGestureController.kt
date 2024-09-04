package com.getcode.view.main.camera

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
    invertedDragEnabled: Boolean,
    private val gesturesEnabled: Boolean,
    private val cameraControl: CameraControl,
    private val cameraInfo: CameraInfo,
    onTap: (Offset) -> MeteringPoint,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var shouldIgnoreScroll = false
    private var resetIgnore: Runnable? = null
    private var initialZoomLevel = 0f
    private var accumulatedDelta = 0f

    private val minZoom get() = cameraInfo.zoomState.value?.minZoomRatio ?: 1f
    private val maxZoom get() = cameraInfo.zoomState.value?.maxZoomRatio ?: 1f

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
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

                val clampedZoomRatio = newZoomRatio.coerceIn(minZoom, maxZoom)

                cameraControl.setZoomRatio(clampedZoomRatio)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                initialZoomLevel = cameraInfo.zoomState.value?.zoomRatio ?: 1f
                resetIgnore = Runnable { shouldIgnoreScroll = false }
                resetIgnore?.let { handler.postDelayed(it, 500) }
            }
        })

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent): Boolean {
                initialZoomLevel = cameraInfo.zoomState.value?.zoomRatio ?: 1f
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

                    val zoomRange = maxZoom - minZoom
                    val zoomDelta = ease(
                        value = accumulatedDelta,
                        fromRange = 0f..250f,
                        toRange = 0f..zoomRange,
                        easeIn = true,
                        easeOut = false
                    )

                    val newZoom = (initialZoomLevel + zoomDelta).coerceIn(minZoom, maxZoom)
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
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_UP) {
                animateZoomReset(cameraInfo, cameraControl)
                initialZoomLevel = cameraInfo.zoomState.value?.zoomRatio ?: 1f
            }
        }
    }

    private fun animateZoomReset(cameraInfo: CameraInfo?, cameraControl: CameraControl?) {
        val durationMs = 300L
        val frameInterval = 16L
        val maxSteps = durationMs / frameInterval
        val currentZoomRatio = cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

        val decrement = (currentZoomRatio - minZoom) / maxSteps

        var currentStep = 0L
        handler.post(object : Runnable {
            override fun run() {
                if (currentStep < maxSteps) {
                    val newZoomRatio = currentZoomRatio - (decrement * currentStep)
                    cameraControl?.setZoomRatio(newZoomRatio.coerceIn(minZoom, maxZoom))
                    currentStep++
                    handler.postDelayed(this, frameInterval)
                } else {
                    cameraControl?.setZoomRatio(minZoom)
                }
            }
        })
    }

    private fun ease(
        value: Float,
        fromRange: ClosedFloatingPointRange<Float>,
        toRange: ClosedFloatingPointRange<Float>,
        easeIn: Boolean,
        easeOut: Boolean
    ): Float {
        val normalizedValue = (value - fromRange.start) / (fromRange.endInclusive - fromRange.start)

        val easedValue: Float = if (easeIn && easeOut) {
            if (normalizedValue < 0.5f) {
                4 * normalizedValue * normalizedValue * normalizedValue
            } else {
                1 - (-2 * normalizedValue + 2).toDouble().pow(3.0).toFloat() / 2
            }
        } else if (easeIn) {
            normalizedValue * normalizedValue * normalizedValue
        } else if (easeOut) {
            1 - (1 - normalizedValue).toDouble().pow(3.0).toFloat()
        } else {
            normalizedValue
        }

        return easedValue * (toRange.endInclusive - toRange.start) + toRange.start
    }
}
