package com.flipcash.app.blob

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import javax.inject.Inject
import javax.inject.Singleton

/** [ImageCodec] over Skia, which ships with Compose Desktop. */
@Singleton
class SkiaImageCodec @Inject constructor() : ImageCodec {

    override fun sourceSize(photo: PickedPhoto): PixelSize? =
        photo.readBytes()?.let(::skiaSizeOf)?.let { PixelSize(it.displayWidth, it.displayHeight) }

    override fun decode(photo: PickedPhoto, limits: ChatMediaLimits): Result<DecodedImage> = runCatching {
        val bytes = checkNotNull(photo.readBytes()) { "Unreadable photo" }
        val image = checkNotNull(
            renderSkiaImage(bytes, flatten = true) { w, h ->
                ChatMediaDownscale.target(w, h, limits.maxWidth, limits.maxHeight, limits.maxPixels)
            }
        ) { "Undecodable photo" }
        SkiaDecodedImage(image)
    }

    override fun thumbnail(jpeg: ByteArray, maxEdge: Int): ArgbThumbnail? {
        val size = skiaSizeOf(jpeg) ?: return null
        val image = renderSkiaImage(jpeg, flatten = true) { w, h ->
            val scale = minOf(1f, maxEdge.toFloat() / maxOf(w, h))
            PixelSize(maxOf((w * scale).toInt(), 1), maxOf((h * scale).toInt(), 1))
        } ?: return null
        return image.use {
            val bgra = it.peekPixels()?.buffer?.bytes ?: return null
            val pixels = IntArray(it.width * it.height) { i ->
                val b = bgra[i * 4].toInt() and 0xFF
                val g = bgra[i * 4 + 1].toInt() and 0xFF
                val r = bgra[i * 4 + 2].toInt() and 0xFF
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            ArgbThumbnail(pixels, it.width, it.height, size.displayWidth, size.displayHeight)
        }
    }

    private class SkiaDecodedImage(private val image: Image) : DecodedImage {
        override val width: Int = image.width
        override val height: Int = image.height

        override fun compressJpeg(quality: Int): ByteArray? =
            image.encodeToData(EncodedImageFormat.JPEG, quality)?.bytes

        override fun close() = image.close()
    }
}
