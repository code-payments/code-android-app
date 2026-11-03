package com.flipcash.app.blob

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.Surface

/** The stored size of the image in [bytes] and the size it displays at once its EXIF rotation is applied. */
internal class SkiaImageSize(val storedWidth: Int, val storedHeight: Int, val swapped: Boolean) {
    val displayWidth: Int get() = if (swapped) storedHeight else storedWidth
    val displayHeight: Int get() = if (swapped) storedWidth else storedHeight
}

internal fun skiaSizeOf(bytes: ByteArray): SkiaImageSize? = runCatching {
    Codec.makeFromData(Data.makeFromBytes(bytes)).use { codec ->
        SkiaImageSize(codec.width, codec.height, codec.encodedOrigin.swapsWidthHeight())
    }
}.getOrNull()

/**
 * Decodes [bytes], applies the EXIF rotation, and draws the result at the size [target] picks for the
 * display-space dimensions. With [flatten] the picture is composited on white, which JPEG needs
 * because it has no alpha; without it, transparency is kept.
 */
internal fun renderSkiaImage(bytes: ByteArray, flatten: Boolean, target: (width: Int, height: Int) -> PixelSize): Image? =
    runCatching {
        val origin = Codec.makeFromData(Data.makeFromBytes(bytes)).use { it.encodedOrigin }
        val image = Image.makeFromEncoded(bytes)
        val stored = SkiaImageSize(image.width, image.height, origin.swapsWidthHeight())
        val size = target(stored.displayWidth, stored.displayHeight)
        Surface.makeRasterN32Premul(size.width, size.height).use { surface ->
            val canvas: Canvas = surface.canvas
            if (flatten) canvas.clear(WHITE)
            canvas.scale(size.width.toFloat() / stored.displayWidth, size.height.toFloat() / stored.displayHeight)
            canvas.concat(origin.toMatrix(stored.storedWidth, stored.storedHeight))
            val bounds = Rect.makeWH(stored.storedWidth.toFloat(), stored.storedHeight.toFloat())
            canvas.drawImageRect(image, bounds, bounds, FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true)
            surface.makeImageSnapshot()
        }
    }.getOrNull()

private const val WHITE = 0xFFFFFFFF.toInt()
