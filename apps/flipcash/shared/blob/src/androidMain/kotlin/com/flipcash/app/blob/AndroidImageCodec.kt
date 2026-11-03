package com.flipcash.app.blob

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/** [ImageCodec] over `ImageDecoder`, `BitmapFactory` and `Bitmap`. */
internal class AndroidImageCodec @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ImageCodec {

    override fun sourceSize(photo: PickedPhoto): PixelSize? = runCatching {
        photo.open()?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) PixelSize(options.outWidth, options.outHeight) else null
        }
    }.getOrNull()

    /**
     * [ImageDecoder] applies the EXIF orientation and reports `info.size` in that display space,
     * so the target is computed from it as-is and the bitmap's width is what ships as the width.
     */
    override fun decode(photo: PickedPhoto, limits: ChatMediaLimits): Result<DecodedImage> = runCatching {
        val source = when (photo) {
            is UriPickedPhoto -> ImageDecoder.createSource(context.contentResolver, photo.uri)
            else -> ImageDecoder.createSource(ByteBuffer.wrap(checkNotNull(photo.readBytes()) { "Unreadable photo" }))
        }
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val target = ChatMediaDownscale.target(
                width = info.size.width,
                height = info.size.height,
                maxWidth = limits.maxWidth,
                maxHeight = limits.maxHeight,
                maxPixels = limits.maxPixels,
            )
            decoder.setTargetSize(target.width, target.height)
        }
        // JPEG has no alpha; compositing on white keeps a transparent PNG from going black.
        val flattened = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, 0f, 0f, null)
        }
        decoded.recycle()
        BitmapImage(flattened)
    }

    override fun thumbnail(jpeg: ByteArray, maxEdge: Int): ArgbThumbnail? = runCatching {
        val source = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        try {
            val scale = min(1f, maxEdge.toFloat() / max(source.width, source.height))
            val tw = max((source.width * scale).toInt(), 1)
            val th = max((source.height * scale).toInt(), 1)
            val thumb = Bitmap.createScaledBitmap(source, tw, th, true)
            val pixels = IntArray(tw * th)
            thumb.getPixels(pixels, 0, tw, 0, 0, tw, th)
            if (thumb !== source) thumb.recycle()
            ArgbThumbnail(pixels, tw, th, source.width, source.height)
        } finally {
            source.recycle()
        }
    }.getOrNull()

    private class BitmapImage(private val bitmap: Bitmap) : DecodedImage {
        override val width: Int = bitmap.width
        override val height: Int = bitmap.height

        override fun compressJpeg(quality: Int): ByteArray? {
            val out = ByteArrayOutputStream()
            return if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) out.toByteArray() else null
        }

        override fun close() = bitmap.recycle()
    }
}
