package com.kik.kikx.kikcodes.implementation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Environment
import com.getcode.BuildConfig
import com.getcode.analytics.AnalyticsService
import com.getcode.util.save
import com.getcode.util.toByteArray
import com.getcode.util.uriToBitmap
import com.getcode.utils.TraceType
import com.getcode.utils.timedTraceSuspend
import com.kik.kikx.kikcodes.KikCodeScanner
import com.kik.kikx.kikcodes.ScanQuality
import com.kik.kikx.models.ScannableKikCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class StaticImageHelper @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val scanner: KikCodeScanner,
    private val analytics: AnalyticsService,
) {
    suspend fun analyze(uri: Uri): Result<ScannableKikCode> {
        val bitmap = context.uriToBitmap(uri)
        return if (bitmap != null) {
            detectCodeInImage(bitmap) { image, quality ->
                scanner.scanKikCode(
                    image.toByteArray(),
                    image.width,
                    image.height,
                    quality
                )
            }
        } else {
            Result.failure(KikCodeScanner.NoKikCodeFoundException())
        }
    }

    private suspend fun detectCodeInImage(
        bitmap: Bitmap,
        scan: suspend (Bitmap, ScanQuality) -> Result<ScannableKikCode>
    ): Result<ScannableKikCode> = withContext(Dispatchers.Default) {
        val destinationRoot =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val date: DateFormat = SimpleDateFormat("yyyy-MM-dd-H-mm", Locale.CANADA)
        val destination = File(destinationRoot, date.format(Date()))
        if (!destination.exists()) {
            destination.mkdirs()
        }

        // Start searching
        return@withContext search(bitmap, destination, scan)
    }

    private suspend fun search(
        bitmap: Bitmap,
        destination: File,
        scan: suspend (Bitmap, ScanQuality) -> Result<ScannableKikCode>,
    ): Result<ScannableKikCode> {
        return timedTraceSuspend(
            message = "analyzing image",
            tag = "Image Analysis",
            type = TraceType.Process,
            onComplete = { result, time ->
                analytics.photoScanned(result.isSuccess, time.inWholeMilliseconds)
            }
        ) {
            // try scanning raw at various scan qualities
            for (quality in ScanQuality.iterator()) {
                val raw = scan(bitmap, quality)
                if (raw.isSuccess) {
                    debugPrint("Code found raw using $quality")
                    bitmap.recycle()
                    return@timedTraceSuspend raw
                } else {
                    debugPrint("No Code found via raw using $quality")
                }
            }

            val zoomLevels = listOf(1.0)
            val result = slidingWindowSearch(
                bitmap = bitmap,
                destination = destination,
                zoomLevels = zoomLevels,
                scan = { scan(it, ScanQuality.Medium) },
            )

            if (result.isSuccess) {
                debugPrint("Code found via sliding window")
            } else {
                debugPrint("No Code found via sliding window")
            }

            bitmap.recycle()
            return@timedTraceSuspend result
        }
    }

    private suspend fun slidingWindowSearch(
        bitmap: Bitmap,
        destination: File,
        zoomLevels: List<Double>,
        scan: suspend (Bitmap) -> Result<ScannableKikCode>
    ): Result<ScannableKikCode> {
        return timedTraceSuspend(
            message = "slidingWindowSearch",
            tag = "Image Analysis",
            type = TraceType.Process
        ) {
            val windows = generateGrid(bitmap.width, bitmap.height)

            // attempt at various zoom levels
            for (zoomLevel in zoomLevels) {
                // Process windows starting from center out
                for ((index, windowRect) in windows.iterateCenterOut().withIndex()) {
                    // Crop the image for the current window
                    val windowBitmap = Bitmap.createBitmap(
                        bitmap,
                        windowRect.left,
                        windowRect.top,
                        windowRect.width(),
                        windowRect.height()
                    )

                    val zoomedBitmap = zoomBitmap(windowBitmap, zoomLevel)

                    // Ensure the bitmap is copied to avoid memory issues
                    val processedBitmap = windowBitmap.copy(windowBitmap.config, false)

                    saveSegment(processedBitmap, destination) {
                        "${index}@${zoomLevel}_${windowRect}.png"
                    }


                    val result = scan(processedBitmap)

                    // Recycle bitmaps to avoid memory leaks
                    zoomedBitmap.recycle()
                    processedBitmap.recycle()
                    windowBitmap.recycle()

                    if (result.isSuccess) {
                        bitmap.recycle()
                        return@timedTraceSuspend result
                    }
                }
            }

            return@timedTraceSuspend Result.failure(KikCodeScanner.NoKikCodeFoundException())
        }
    }

    private fun generateGrid(
        width: Int,
        height: Int,
        rows: Int = 6,
        columns: Int = 6
    ): List<Rect> {
        val windows = mutableListOf<Rect>()

        // Calculate window size for a rows x columns grid
        val windowWidth = width / columns
        val windowHeight = height / rows

        // Define overlap as 50% of the window size
        val stepX = windowWidth / 2
        val stepY = windowHeight / 2

        // Iterate through the grid top to bottom, left to right
        val rowCount = height / stepY
        for (r in 0 until rowCount) {
            val columnCount = width / stepX
            for (c in 0 until columnCount) {
                val x = c * stepX
                val y = r * stepY

                // Ensure right and bottom do not exceed bitmap bounds
                val right = (x + windowWidth).coerceAtMost(width)
                val bottom = (y + windowHeight).coerceAtMost(height)

                val windowRect = Rect(x, y, right, bottom)
                windows.add(windowRect)
            }
        }

        return windows
    }

    // Extension function to iterate through windows center out
    private fun <T> List<T>.iterateCenterOut(): List<T> {
        val center = size / 2
        val ordered = mutableListOf<T>()

        for (i in 0 until center) {
            ordered.add(this[center - i])
            if (center + i < size) {
                ordered.add(this[center + i])
            }
        }
        return ordered
    }

    private fun zoomBitmap(bitmap: Bitmap, zoomLevel: Double): Bitmap {
        // If zoomLevel is 1.0, just return a copy of the original bitmap (to prevent recycling issues)
        if (zoomLevel == 1.0) return Bitmap.createBitmap(bitmap)

        val cropWidth = (bitmap.width / zoomLevel).toInt()
        val cropHeight = (bitmap.height / zoomLevel).toInt()
        val xOffset = (bitmap.width - cropWidth) / 2
        val yOffset = (bitmap.height - cropHeight) / 2

        val croppedBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, cropWidth, cropHeight)
        val scaledBitmap =
            Bitmap.createScaledBitmap(croppedBitmap, bitmap.width, bitmap.height, true)

        croppedBitmap.recycle()
        return scaledBitmap
    }

    private fun saveSegment(bitmap: Bitmap, destination: File, name: () -> String) {
        debugPrint("Scanning ${name().substringBeforeLast(".")}")
        if (SAVE_IMAGES) {
            bitmap.save(destination, name)
        }
    }
}

private fun debugPrint(message: String) {
    if (DEBUG) println(message)
}

private val DEBUG = BuildConfig.DEBUG
private const val SAVE_IMAGES = false