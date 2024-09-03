package com.kik.kikx.kikcodes.implementation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.getcode.media.MediaScanner
import com.getcode.util.save
import com.getcode.util.toByteArray
import com.getcode.util.uriToBitmap
import com.getcode.utils.ErrorUtils
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.models.ScannableKikCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    @Inject
    internal lateinit var mediaScanner: MediaScanner

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
                detectCodeInImage(bitmap) {
                    scanner.scanKikCode(
                        it.toByteArray(),
                        it.width,
                        it.height,
                    )
                }.onSuccess { result ->
                    onCodeScanned(result)

                }.onFailure { error ->
                    when (error) {
                        is KikCodeScanner.NoKikCodeFoundException -> onNoCodeFound()
                        else -> ErrorUtils.handleError(error)
                    }
                }
            }
        }
    }

    private suspend fun detectCodeInImage(
        bitmap: Bitmap,
        minSectionSize: Int = 100,
        scan: suspend (Bitmap) -> Result<ScannableKikCode>
    ): Result<ScannableKikCode> = withContext(Dispatchers.Default) {
        val destinationRoot =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val date: DateFormat = SimpleDateFormat("yyyy-MM-dd-H-mm", Locale.CANADA)
        val destination = File(destinationRoot, date.format(Date()))
        if (!destination.exists()) {
            destination.mkdirs()
        }

        // Start the recursive division and scanning process
        return@withContext divideAndScan(bitmap, destination, minSectionSize, scan)
    }

    private suspend fun divideAndScan(
        bitmap: Bitmap,
        destination: File,
        minSectionSize: Int,
        scan: suspend (Bitmap) -> Result<ScannableKikCode>,
    ): Result<ScannableKikCode> {
        val zoomLevels = listOf(1.0, 2.0, 5.0, 10.0)

        return processBitmapRecursively(bitmap, destination, minSectionSize, scan, zoomLevels)
    }

    private suspend fun processBitmapRecursively(
        bitmap: Bitmap,
        destination: File,
        minSectionSize: Int,
        scan: suspend (Bitmap) -> Result<ScannableKikCode>,
        zoomLevels: List<Double>
    ): Result<ScannableKikCode> {
        val width = bitmap.width
        val height = bitmap.height

        // Base case: If the bitmap is smaller than the minimum section size, process it directly
        if (width <= minSectionSize || height <= minSectionSize) {
            return scanWithZoomLevels(bitmap, destination, scan, zoomLevels)
        }

        // Scan the center section first
        val centerRect = calculateCenterRect(width, height)
        val centerBitmap = Bitmap.createBitmap(bitmap, centerRect.left, centerRect.top, centerRect.width(), centerRect.height())

        val centerResult = scanWithZoomLevels(centerBitmap, destination, scan, zoomLevels)
        centerBitmap.recycle()

        if (centerResult.isSuccess) {
            return centerResult
        }

        // Divide the bitmap into left and right halves and process recursively
        val leftHalf = Bitmap.createBitmap(bitmap, 0, 0, width / 2, height)
        val rightHalf = Bitmap.createBitmap(bitmap, width / 2, 0, width / 2, height)

        val leftResult = processBitmapRecursively(leftHalf, destination, minSectionSize, scan, zoomLevels)
        leftHalf.recycle()

        if (leftResult.isSuccess) {
            rightHalf.recycle()
            return leftResult
        }

        val rightResult = processBitmapRecursively(rightHalf, destination, minSectionSize, scan, zoomLevels)
        rightHalf.recycle()

        return rightResult
    }

    private suspend fun scanWithZoomLevels(
        bitmap: Bitmap,
        destination: File,
        scan: suspend (Bitmap) -> Result<ScannableKikCode>,
        zoomLevels: List<Double>
    ): Result<ScannableKikCode> {
        for (zoomLevel in zoomLevels) {
            val zoomedBitmap = zoomBitmap(bitmap, zoomLevel)
            saveSegment(zoomedBitmap, destination) {
                "section_${zoomedBitmap.width}x${zoomedBitmap.height}_zoom${zoomLevel}.png"
            }
            val result = scan(zoomedBitmap)

            zoomedBitmap.recycle()

            if (result.isSuccess) {
                return result
            }
        }

        return Result.failure(Exception("No successful scan"))
    }

    private fun saveSegment(bitmap: Bitmap, destination: File, name: () -> String) {
        if (DEBUG) {
            bitmap.save(destination, name)
        }
    }

    private fun zoomBitmap(bitmap: Bitmap, zoomLevel: Double): Bitmap {
        // If zoomLevel is 1.0, just return a copy of the original bitmap (to prevent recycling issues)
        if (zoomLevel == 1.0) return Bitmap.createBitmap(bitmap)

        val cropWidth = (bitmap.width / zoomLevel).toInt()
        val cropHeight = (bitmap.height / zoomLevel).toInt()
        val xOffset = (bitmap.width - cropWidth) / 2
        val yOffset = (bitmap.height - cropHeight) / 2

        val croppedBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, cropWidth, cropHeight)
        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, bitmap.width, bitmap.height, true)

        croppedBitmap.recycle()
        return scaledBitmap
    }

    private fun calculateCenterRect(width: Int, height: Int): Rect {
        val centerWidth = width / 2
        val centerHeight = height / 2
        return Rect(
            centerWidth / 2,
            centerHeight / 2,
            centerWidth + centerWidth / 2,
            centerHeight + centerHeight / 2
        )
    }
}

private const val DEBUG = false