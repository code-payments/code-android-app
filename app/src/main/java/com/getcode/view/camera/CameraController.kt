package com.getcode.view.camera

import android.graphics.Matrix
import android.view.View
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.kin.sdk.base.tools.Optional
import java.net.URL

interface CameraController {

    enum class FlashMode {
        AUTO,
        ON,
        OFF
    }

    enum class CameraPosition {
        FRONT,
        REAR
    }

    data class PreviewSize(
        val width: Int,
        val height: Int,
        val rotation: Int
    ) {
        fun withoutRotation(): PreviewSize {
            return when (rotation) {
                0 -> this
                90 -> PreviewSize(height, width, 0)
                180 -> PreviewSize(width, height, 0)
                270 -> PreviewSize(height, width, 0)
                else -> throw Exception("Unsupported rotation")
            }
        }
    }

    data class PictureResult(
        val pictureData: ByteArray,
        val transformationMatrix: Matrix
    )

    data class VideoResult(
        val videoUrl: URL,
        val duration: Long,
        val rotation: Int
    )

    fun flashMode(): Flowable<Optional<FlashMode>>

    //    fun screenFlash(): Flowable<Boolean>

    fun previewView(): View

    fun recordingVideo(): Flowable<Boolean>

    fun recordingVideoStartTime(): Flowable<Optional<Long>>

    fun isAdjustingFocus(): Flowable<Boolean>

    fun isPreviewing(): Flowable<Boolean>

    fun startPreview()

    fun failedToStartPreview(): Flowable<Boolean>

    fun stopPreview()

    fun getPreviewBuffer(): Single<ByteArray>

    fun takePicture(shutterCallback: () -> Unit): Single<PictureResult>

    fun rotateFlashMode()

    fun switchCamera()

    fun cameraPosition(): Flowable<Optional<CameraPosition>>

    /**
     * Starts recording a video with the specified device orientation. The device orientation allows
     * us to output a video with the right rotation (if recorded in landscape, the video output
     * should be in landscape)
     */
    fun startRecording(outputUrl: URL, maxDurationMs: Long, deviceOrientation: Int)

    fun stopRecording(): Single<VideoResult>

    /**
     * Sets the camera focus and exposure reference at the specified point.
     * The provided floats should be between 0 and 1
     */
    fun focusAt(relativeX: Float, relativeY: Float)

    fun zoomBy(scaleChange: Float)

    fun setZoom(zoomScale: Float)

    fun currentZoom(): Float?

    fun previewSize(): Flowable<Optional<PreviewSize>>
}
