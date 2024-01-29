package com.getcode.view.camera

import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.processors.BehaviorProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kin.sdk.base.tools.Optional
import timber.log.Timber
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("DEPRECATION")
class LegacyCameraController(
    private val context: Context
) : CameraController {

    companion object {
        private val FLASH_MODE_MAP = mapOf(
            CameraController.FlashMode.AUTO to Camera.Parameters.FLASH_MODE_AUTO,
            CameraController.FlashMode.ON to Camera.Parameters.FLASH_MODE_ON,
            CameraController.FlashMode.OFF to Camera.Parameters.FLASH_MODE_OFF
        )
        private val FLASH_MODE_ROTATION = arrayOf(
            CameraController.FlashMode.AUTO,
            CameraController.FlashMode.OFF,
            CameraController.FlashMode.ON
        )
        private const val VIDEO_BITRATE = 960000   // bits / s
        private const val MAX_VIDEO_DIMENSION = 1280
        private const val MIN_VIDEO_DIMENSION = 480
    }

    private fun Camera.safeCancelAutoFocus() {
        try {
            cancelAutoFocus()
        } catch (_: RuntimeException) {
        }
    }

    private fun Camera.safeAutoFocus(callback: () -> Unit) {
        try {
            autoFocus { _, _ -> callback() }
        } catch (_: RuntimeException) {
            callback()
        }
    }

    private val flashModeSubject = BehaviorProcessor.createDefault(Optional.empty<CameraController.FlashMode>())
    private val previewSizeSubject = BehaviorProcessor.createDefault(Optional.empty<CameraController.PreviewSize>())
    private val adjustingFocusSubject = BehaviorProcessor.createDefault(false)
    private val cameraPositionSubject = BehaviorProcessor.createDefault(Optional.empty<CameraController.CameraPosition>())
    private val previewFailedSubject = BehaviorProcessor.createDefault(false)

    private data class RecordingTuple(
        val recordingStart: Long,
        val rotation: Int
    )

    private val recordingStartSubject = BehaviorProcessor.createDefault(Optional.empty<RecordingTuple>())

    private val previewView = PreviewView(context)
    private var camera: Camera? = null
    private var cameraIndex: Int? = null
    private var rotation = 0
    private var shouldBePreviewing = false
    private var mediaRecorder: MediaRecorder? = null
    private var videoOutputUrl: URL? = null

    private var zoomScale: Float? = null

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun flashMode(): Flowable<Optional<CameraController.FlashMode>> {
        return flashModeSubject
    }

    override fun previewView(): View {
        return previewView
    }

    override fun isPreviewing(): Flowable<Boolean> = previewSizeSubject.map { it.isPresent }

    override fun startPreview() {
        shouldBePreviewing = true

        adjustingFocusSubject.onNext(false)

        if (!previewView.ready) {
            return
        }

        if (camera != null) {
            return
        }

        val cameraIndex = cameraIndex ?: return

        scope.launch {
            try {
                previewFailedSubject.onNext(false)
                camera = Camera.open(cameraIndex)
            } catch (ex: Throwable) {
                previewFailedSubject.onNext(true)
                return@launch
            }
            zoomScale = 0f

            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(cameraIndex, cameraInfo)
            cameraPositionSubject.onNext(
                Optional.of(
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        CameraController.CameraPosition.FRONT
                    } else {
                        CameraController.CameraPosition.REAR
                    }
                )
            )

            camera?.apply {
                setPreviewDisplay(previewView.holder)

                val parameters = parameters

                val pictureSize = findClosestRatio(parameters.supportedPictureSizes, 4.0 / 3.0)

                // Find the preview that matches the same ratio
                val previewSize = findClosestRatio(
                    parameters.supportedPreviewSizes,
                    pictureSize.width.toDouble() / pictureSize.height
                )

                parameters.setPreviewSize(previewSize.width, previewSize.height)
                parameters.setPictureSize(pictureSize.width, pictureSize.height)

                // By default, set the camera to autofocus (until an explicit focus point is specified)
                parameters.focusMode = findBestFocusMode(parameters.supportedFocusModes)

                this.parameters = parameters

                startPreview()

                // https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
                val display =
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

                rotation = when (display.rotation) {
                    Surface.ROTATION_0 -> 0
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> throw Exception("Unexpected orientation")
                }

                val previewRotation =
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        360 - ((cameraInfo.orientation + rotation) % 360)
                    } else {
                        cameraInfo.orientation - rotation + 360
                    } % 360

                setDisplayOrientation(previewRotation)

                previewSizeSubject.onNext(
                    Optional.of(
                        CameraController.PreviewSize(
                            parameters.previewSize.width,
                            parameters.previewSize.height,
                            previewRotation
                        )
                    )
                )
            }

            // If the flash mode that we were in is not supported, or if we didn't have a flash mode, force a rotation
            val currentFlashMode = flashModeSubject.value!!.get()
            if (currentFlashMode == null || !cameraSupportsFlashMode(camera!!, currentFlashMode)) {
                rotateFlashMode()
            }
        }
    }

    private fun findBestFocusMode(supportedFocusModes: List<String>): String {
        return supportedFocusModes.firstOrNull { it == Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE }
            ?: supportedFocusModes.firstOrNull { it == Camera.Parameters.FOCUS_MODE_AUTO }
            ?: supportedFocusModes.first()
    }

    private fun findSizesThatMatchRatio(sizes: List<Camera.Size>, targetAspectRatio: Double, tolerance: Double): List<Camera.Size> {
        return sizes.filter { size ->
            val aspectRatio = size.width.toDouble() / size.height
            abs(aspectRatio - targetAspectRatio) < tolerance
        }
    }

    private fun findClosestRatio(sizes: List<Camera.Size>, targetAspectRatio: Double): Camera.Size {
        var currentBest = sizes[0]
        sizes.forEach { size ->
            val ratio = size.width.toDouble() / size.height
            val currentBestRatio = currentBest.width.toDouble() / currentBest.height

            if (abs(targetAspectRatio - ratio) < abs(targetAspectRatio - currentBestRatio)) {
                currentBest = size
            }
        }
        return currentBest
    }

    /**
     * Selects the optimal recording size using the following rules:
     * - size must match the specified aspect ratio. If no size matches that ratio, the size that is the closest to that ratio is returned
     * - size must be within the specified min/max dimensions. If no size with the right ratio is found within those bounds, this rule is ignored
     * - size has the smallest amount of pixels possible (within the specified ratio and max dimensions). This is important otherwise recording a large video at the bitrate we specify can result in very serious compression artifacts.
     */
    private fun findOptimalRecordingSize(sizes: List<Camera.Size>, targetAspectRatio: Double, minDesiredDimension: Int, maxAllowedDimension: Int): Camera.Size {
        val optionsWithMatchingRatio = findSizesThatMatchRatio(sizes, targetAspectRatio, 0.1)

        if (optionsWithMatchingRatio.isEmpty()) {
            return findClosestRatio(sizes, targetAspectRatio) // nothing matches the ratio, fall back to the closest one
        }

        val optionsWithinAllowedDimensions = optionsWithMatchingRatio.filter { size ->
            val maxSizeDimension = max(size.width, size.height)
            val minSizeDimension = min(size.width, size.height)
            minSizeDimension >= minDesiredDimension && maxSizeDimension <= maxAllowedDimension
        }

        val consideredOptions = if (optionsWithinAllowedDimensions.isNotEmpty()) {
            optionsWithinAllowedDimensions
        } else {
            optionsWithMatchingRatio // nothing within the allowed dimensions, fall back to the allowed ones
        }

        return consideredOptions.minByOrNull { size ->
            size.width.toLong() * size.height.toLong()
        }!!
    }

    override fun stopPreview() {
        shouldBePreviewing = false

        try {
            previewSizeSubject.onNext(Optional.empty())
            cameraPositionSubject.onNext(Optional.empty())
            camera?.stopPreview()
            try {
                camera?.setPreviewDisplay(null)
            } catch (e: Exception) {}
        } finally {
            camera?.release()
            camera = null
            zoomScale = null
        }
    }

    override fun takePicture(shutterCallback: () -> Unit): Single<CameraController.PictureResult> {
        return Single.create {
            camera!!.takePicture(Camera.ShutterCallback {
                shutterCallback()
            }, null, PictureCallback(it))
        }
    }

    override fun rotateFlashMode() {
        if (camera == null) {
            flashModeSubject.onNext(Optional.empty())
            return
        }

        val rotation = FLASH_MODE_ROTATION.filter { cameraSupportsFlashMode(camera!!, it) }
        if (rotation.size <= 1) {
            flashModeSubject.onNext(Optional.empty())
            return
        }

        val currentFlashModeIndex = rotation.indexOf(flashModeSubject.value!!.get())
        val newMode = rotation[(currentFlashModeIndex + 1) % rotation.size]
        flashModeSubject.onNext(Optional.of(newMode))
        setFlashMode(newMode)
    }

    override fun switchCamera() {
        val shouldBePreviewing = shouldBePreviewing

        nextCameraIndex()

        if (shouldBePreviewing) {
            startPreview()
        }
    }

    override fun previewSize(): Flowable<Optional<CameraController.PreviewSize>> = previewSizeSubject

    override fun failedToStartPreview(): Flowable<Boolean> = previewFailedSubject

    private fun nextCameraIndex() {
        stopPreview()
        cameraIndex = getNextCameraInstanceIndex(currentIndex = cameraIndex)
    }

    private fun getNextCameraInstanceIndex(currentIndex: Int? = null): Int? {
        if (Camera.getNumberOfCameras() == 0) {
            return null
        }

        val curIndex = currentIndex ?: -1
        return (curIndex + 1) % Camera.getNumberOfCameras()
    }

    private fun setFlashMode(mode: CameraController.FlashMode) {
        val camera = camera ?: return

        if (!cameraSupportsFlashMode(camera, mode)) {
            return
        }

        val parameters = camera.parameters
        val flashMode = FLASH_MODE_MAP[mode]
        parameters.flashMode = flashMode
        camera.parameters = parameters
    }

    private fun previewViewIsReady() {
        if (shouldBePreviewing) {
            startPreview()
        }
    }

    private inner class PreviewView(context: Context) : SurfaceView(context) {
        var ready = false

        init {
            nextCameraIndex()
            holder.addCallback(SurfaceHolderCallback())
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        }

        private inner class SurfaceHolderCallback : SurfaceHolder.Callback {
            override fun surfaceCreated(p0: SurfaceHolder) {
                if (holder.surface == null) {
                    return
                }

                ready = true
                previewViewIsReady()
            }

            override fun surfaceDestroyed(p0: SurfaceHolder) {
                ready = false
            }

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
            }
        }
    }

    private fun cameraSupportsFlashMode(camera: Camera, flashMode: CameraController.FlashMode): Boolean {
        val nativeFlashMode = FLASH_MODE_MAP[flashMode]

        val parameters = camera.parameters
        return parameters.supportedFlashModes != null && parameters.supportedFlashModes.contains(nativeFlashMode)
    }

    private inner class PictureCallback(
        private val singleEmitter: SingleEmitter<CameraController.PictureResult>
    ) : Camera.PictureCallback {
        override fun onPictureTaken(data: ByteArray?, camera: Camera?) {
            if (data == null) {
                singleEmitter.onError(Exception("Error getting picture data"))
                return
            }

            val previewRotation = previewSizeSubject.value!!.get()!!.rotation

            val matrix = Matrix()

            // When using the front camera, we need to flip it vertically
            // https://stackoverflow.com/questions/10283467/android-front-facing-camera-taking-inverted-photos
            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(cameraIndex!!, cameraInfo)
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                val mirrorMatrix = Matrix()
                mirrorMatrix.setValues(floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
                matrix.postConcat(mirrorMatrix)
            }

            matrix.postRotate(previewRotation.toFloat())

            singleEmitter.onSuccess(CameraController.PictureResult(data, matrix))
        }
    }

    override fun focusAt(relativeX: Float, relativeY: Float) {
        if (relativeX !in 0f..1f || relativeY !in 0f..1f) {
            Timber.i("Invalid focus point provided ($relativeX,$relativeY. Focus point coordinates should be between 0 and 1")
            return
        }

        val camera = camera ?: return

        camera.safeCancelAutoFocus()

        // Camera needs the points to be between -1000 and 1000 (yay android)
        val relativeXInCameraBounds = (relativeX * 2 - 1) * 1000
        val relativeYInCameraBounds = (relativeY * 2 - 1) * 1000

        val parameters = camera.parameters
        val focusRect = Rect(
            (relativeXInCameraBounds - 100).coerceIn(-999f..999f).toInt(),
            (relativeYInCameraBounds - 100).coerceIn(-999f..999f).toInt(),
            (relativeXInCameraBounds + 100).coerceIn(-999f..999f).toInt(),
            (relativeYInCameraBounds + 100).coerceIn(-999f..999f).toInt())

        if (parameters.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_AUTO) == true) {
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
        }
        if (parameters.maxNumFocusAreas > 0) {
            parameters.focusAreas = listOf(Camera.Area(focusRect, 1000))
        }
        if (parameters.maxNumMeteringAreas > 0) {
            parameters.meteringAreas = listOf(Camera.Area(focusRect, 1000))
        }
        camera.parameters = parameters

        adjustingFocusSubject.onNext(true)
        camera.safeAutoFocus {
            adjustingFocusSubject.onNext(false)
        }
    }

    override fun isAdjustingFocus(): Flowable<Boolean> = adjustingFocusSubject

    override fun zoomBy(scaleChange: Float) {
        if (camera == null) {
            return
        }
        setZoom(currentZoom()!! + scaleChange)
    }

    override fun setZoom(zoomScale: Float) {
        val camera = camera ?: return
        val parameters = camera.parameters ?: return

        if (!parameters.isZoomSupported) {
            return
        }

        // Using an internal zoomScale float in order to pretend we have sub-integer precision, otherwise we could have
        // issues when zooming very slowly, as the actual Android zoom isn't as precise.
        this.zoomScale = zoomScale.coerceIn(0f, 1f)
        parameters.zoom = (this.zoomScale!! * parameters.maxZoom).toInt()
        camera.parameters = parameters
    }

    override fun getPreviewBuffer(): Single<ByteArray> {
        return previewSizeSubject
            .firstOrError()
            .flatMap { previewSize ->
                if (!previewSize.isPresent) {
                    Single.error(NoPreviewException("No ongoing preview"))
                } else {
                    Single.create<ByteArray> { singleEmitter ->
                        val camera = camera ?: throw CameraException("No camera set on controller. Did you start the preview?")

                        camera.setOneShotPreviewCallback { data, _ ->
//                            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

                            if (data == null) {
                                singleEmitter.onError(CameraException("No data in preview buffer"))
                            } else {
                                singleEmitter.onSuccess(data)
                            }
                        }
                    }
                }
            }
    }

    override fun currentZoom(): Float? = zoomScale

    override fun cameraPosition(): Flowable<Optional<CameraController.CameraPosition>> = cameraPositionSubject

    override fun recordingVideo(): Flowable<Boolean> = recordingStartSubject.map { it.isPresent }

    override fun recordingVideoStartTime(): Flowable<Optional<Long>> = recordingStartSubject.map { it.map { tuple -> tuple.recordingStart } }

    private fun getDefaultVideoProfile(cameraId: Int): CamcorderProfile {
        return if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
            CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P)
        } else CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH)
    }

    override fun startRecording(outputUrl: URL, maxDurationMs: Long, deviceOrientation: Int) {
        val camera = camera ?: throw IllegalStateException("No camera being currently used. Did you call startPreview()?")
        val previewSize = previewSizeSubject.value!!.get()!!

        val cameraParams = camera.parameters
        if (cameraParams.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) == true) {
            cameraParams.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        }
        camera.parameters = cameraParams

        val videoSize = findOptimalRecordingSize(cameraParams.supportedVideoSizes, previewSize.width.toDouble() / previewSize.height, MIN_VIDEO_DIMENSION, MAX_VIDEO_DIMENSION)

        mediaRecorder = MediaRecorder().apply {
            camera.lock()
            camera.unlock()

            setCamera(camera)

            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setVideoSource(MediaRecorder.VideoSource.CAMERA)

            setOrientationHint(if (cameraPositionSubject.value!!.get() == CameraController.CameraPosition.FRONT) {
                360 - ((previewSize.rotation + deviceOrientation) % 360)
            } else {
                (previewSize.rotation + deviceOrientation) % 360
            })

            val camcorderProfile = getDefaultVideoProfile(cameraIndex!!)
            setProfile(camcorderProfile)

            if (VIDEO_BITRATE < camcorderProfile.videoBitRate) {
                setVideoEncodingBitRate(VIDEO_BITRATE)
            }

            setVideoSize(videoSize.width, videoSize.height)

            setOutputFile(outputUrl.toURI().path)
            setMaxDuration(maxDurationMs.toInt())
            prepare()
            start()
        }

        videoOutputUrl = outputUrl

        recordingStartSubject.onNext(Optional.of(RecordingTuple(
            recordingStart = System.currentTimeMillis(),
            rotation = deviceOrientation
        )))
    }

    override fun stopRecording(): Single<CameraController.VideoResult> {
        return Single.fromCallable {
            val videoOutputUrl = videoOutputUrl
            val mediaRecorder = mediaRecorder
            val recordingStart = recordingStartSubject.value!!.get()

            this@LegacyCameraController.videoOutputUrl = null
            this@LegacyCameraController.mediaRecorder = null
            recordingStartSubject.onNext(Optional.empty())

            if (mediaRecorder == null || videoOutputUrl == null || recordingStart == null) {
                throw IllegalStateException("Not recording")
            }

            // Calculating duration before .stop()
            val duration = System.currentTimeMillis() - recordingStart.recordingStart

            try {
                mediaRecorder.stop()
            } finally {
                mediaRecorder.reset()
                mediaRecorder.release()
            }

            CameraController.VideoResult(videoOutputUrl, duration, recordingStart.rotation)
        }
    }

    class NoPreviewException(message: String): Exception(message)
    class CameraException(message: String): Exception(message)
}
