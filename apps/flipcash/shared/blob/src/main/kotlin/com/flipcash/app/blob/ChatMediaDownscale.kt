package com.flipcash.app.blob

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PixelSize(val width: Int, val height: Int)

/**
 * The pixel size a photo is scaled to before encoding. A bound of 0 means unbounded, and an image
 * is never scaled up.
 */
object ChatMediaDownscale {

    /**
     * [width] and [height] are display-space — after the EXIF rotation has been applied.
     *
     * The scale is computed in doubles and truncated, which can land a pixel or two over
     * [maxPixels], so the longer side is then walked down until the area fits. Must agree with iOS
     * (`test-vectors/chat_media.json`, `downscale`) to the pixel.
     */
    fun target(width: Int, height: Int, maxWidth: Int, maxHeight: Int, maxPixels: Long): PixelSize {
        if (width <= 0 || height <= 0) return PixelSize(max(width, 1), max(height, 1))

        var scale = 1.0
        if (maxWidth > 0) scale = min(scale, maxWidth.toDouble() / width)
        if (maxHeight > 0) scale = min(scale, maxHeight.toDouble() / height)
        if (maxPixels > 0) scale = min(scale, sqrt(maxPixels.toDouble() / (width.toDouble() * height)))

        var w = max((width * scale).toInt(), 1)
        var h = max((height * scale).toInt(), 1)

        if (maxPixels > 0) {
            while (w.toLong() * h > maxPixels && (w > 1 || h > 1)) {
                if (w >= h) w-- else h--
            }
        }
        return PixelSize(w, h)
    }
}
