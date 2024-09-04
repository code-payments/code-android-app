package com.kik.kikx.kikcodes.implementation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
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
        return@withContext search(bitmap, destination, 100, scan)
    }

    private suspend fun search(
        bitmap: Bitmap,
        destination: File,
        minSectionSize: Int,
        scan: suspend (Bitmap) -> Result<ScannableKikCode>,
    ): Result<ScannableKikCode> {
        // try scanning raw
        val raw = scan(bitmap)
        if (raw.isSuccess) {
            debugPrint("Code found raw")
            bitmap.recycle()
            return raw
        } else {
            debugPrint("No Code found via raw")
        }

        // attempt quick lookup by recursively splitting image into quadrants, with increasing zoom levels
        val zoomLevels = listOf(1.0, 2.0, 5.0, 10.0)
        val recursiveSearch = processBitmapRecursively(
            bitmap,
            destination,
            minSectionSize,
            scan,
            zoomLevels,
            ""
        )

        if (recursiveSearch.isSuccess) {
            debugPrint("Code found via recursive lookup")
            bitmap.recycle()
            return recursiveSearch
        } else {
            debugPrint("No Code found via recursive lookup")
        }

        val result = slidingWindowSearch(
            bitmap = bitmap,
            windowSize = 300,
            stepSize = 150,
            scan = scan,
            zoomLevels = zoomLevels
        )

        if (result.isSuccess) {
            debugPrint("Code found via sliding window")
        }

        bitmap.recycle()
        return result
    }

    private suspend fun slidingWindowSearch(
        bitmap: Bitmap,
        windowSize: Int,
        stepSize: Int,
        scan: suspend (Bitmap) -> Result<ScannableKikCode>,
        zoomLevels: List<Double>
    ): Result<ScannableKikCode> {
        val w = bitmap.width
        val h = bitmap.height

        debugPrint("search: original ${w}x${h}")

        for (zoomLevel in zoomLevels) {
            val windowWidth = (windowSize * zoomLevel).toInt()
            val windowHeight = (windowSize * zoomLevel).toInt()

            for (i in 0 until w step stepSize) {
                for (j in 0 until h step stepSize) {
                    val x = i.coerceAtMost(w - windowWidth)
                    val y = j.coerceAtMost(h - windowHeight)
                    val width = windowWidth.coerceAtMost(w - x)
                    val height = windowHeight.coerceAtMost(h - y)
                    val windowBitmap = Bitmap.createBitmap(
                        bitmap,
                        x, y,
                        width, height
                    )

                    debugPrint("search: checking {x: $x, y: $y, w: $width, h: $height} @ $zoomLevel")
                    val result = scan(windowBitmap)
                    windowBitmap.recycle()

                    if (result.isSuccess) {
                        debugPrint("search: SUCCESS in {x: $x, y: $y, w: $width, h: $height} @ $zoomLevel")
                        return result
                    }
                }
            }
        }

        return Result.failure(KikCodeScanner.NoKikCodeFoundException())
    }

    private suspend fun processBitmapRecursively(
        bitmap: Bitmap,
        destination: File,
        minSectionSize: Int,
        scan: suspend (Bitmap) -> Result<ScannableKikCode>,
        zoomLevels: List<Double>,
        regionName: String
    ): Result<ScannableKikCode> {
        val width = bitmap.width
        val height = bitmap.height

        // Base case: If the bitmap is smaller than the minimum section size, process it directly
        if (width <= minSectionSize || height <= minSectionSize) {
            return scanWithZoomLevels(bitmap, destination, scan, zoomLevels, regionName)
        }

        val centerRect = calculateCenterRect(width, height)
        val centerBitmap = Bitmap.createBitmap(bitmap, centerRect.left, centerRect.top, centerRect.width(), centerRect.height())

        val centerResult = scanWithZoomLevels(centerBitmap, destination, scan, zoomLevels, "center")
        centerBitmap.recycle()

        if (centerResult.isSuccess) {
            return centerResult
        }

        val quadrants = splitIntoQuadrants(bitmap)

        // Process each quadrant recursively
        for ((quadrantBitmap, name) in quadrants) {
            val quadrantResult = processBitmapRecursively(quadrantBitmap, destination, minSectionSize, scan, zoomLevels, name)
            quadrantBitmap.recycle()

            if (quadrantResult.isSuccess) {
                return quadrantResult
            }
        }

        return Result.failure(KikCodeScanner.NoKikCodeFoundException())
    }

    private fun splitIntoQuadrants(bitmap: Bitmap): List<Pair<Bitmap, String>> {
        val width = bitmap.width
        val height = bitmap.height
        val halfWidth = width / 2
        val halfHeight = height / 2

        val topLeft = Bitmap.createBitmap(bitmap, 0, 0, halfWidth, halfHeight)
        val topRight = Bitmap.createBitmap(bitmap, halfWidth, 0, halfWidth, halfHeight)
        val bottomLeft = Bitmap.createBitmap(bitmap, 0, halfHeight, halfWidth, halfHeight)
        val bottomRight = Bitmap.createBitmap(bitmap, halfWidth, halfHeight, halfWidth, halfHeight)

        return listOf(
            topLeft to "topLeft",
            topRight to "topRight",
            bottomLeft to "bottomLeft",
            bottomRight to "bottomRight"
        )
    }

    private suspend fun scanWithZoomLevels(
        bitmap: Bitmap,
        destination: File,
        scan: suspend (Bitmap) -> Result<ScannableKikCode>,
        zoomLevels: List<Double>,
        regionName: String // Use the region name to give unique filenames
    ): Result<ScannableKikCode> {
        for (zoomLevel in zoomLevels) {
            val zoomedBitmap = zoomBitmap(bitmap, zoomLevel)
            saveSegment(zoomedBitmap, destination) {
                val prefix = regionName.ifEmpty { null }?.let { "${it}_"}
                "$prefix${zoomedBitmap.width}x${zoomedBitmap.height}_zoom${zoomLevel}.png"
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

private fun debugPrint(message: String) {
    if (DEBUG) println(message)
}

private const val DEBUG = false