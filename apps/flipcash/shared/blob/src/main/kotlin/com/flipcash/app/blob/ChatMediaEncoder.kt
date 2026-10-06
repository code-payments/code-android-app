package com.flipcash.app.blob

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.internal.extensions.withoutJpegMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a picked or captured photo into the JPEG a chat message uploads: decoded with its EXIF
 * rotation applied, scaled under the policy's bounds, flattened onto white, and compressed at the
 * highest quality that fits the byte cap.
 *
 * The result is always `image/jpeg`. There is no fallback to another type and, for a sealed
 * upload, none to plaintext — a photo that can't be made to fit is refused.
 */
@Singleton
class ChatMediaEncoder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    /** [bytes] are metadata-free; [width] × [height] are display-space pixels. */
    class Encoded(val bytes: ByteArray, val width: Int, val height: Int)

    suspend fun encode(uri: Uri, limits: ChatMediaLimits): Result<Encoded> = withContext(dispatchers.IO) {
        val source = decode(uri, limits)
            .getOrElse { return@withContext Result.failure(ChatMediaEncodingException.EncodingFailed(it)) }
        try {
            JpegLadder.select(cap = limits.maxBytes) { quality -> source.compressJpeg(quality) }
                .map { Encoded(it, source.width, source.height) }
        } finally {
            source.recycle()
        }
    }

    /**
     * [ImageDecoder] applies the EXIF orientation and reports `info.size` in that display space,
     * so the target is computed from it as-is and [Bitmap.getWidth] is what ships as the width.
     */
    private fun decode(uri: Uri, limits: ChatMediaLimits): Result<Bitmap> = runCatching {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
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
        flattened
    }

    private fun Bitmap.compressJpeg(quality: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        return if (compress(Bitmap.CompressFormat.JPEG, quality, out)) out.toByteArray() else null
    }

    companion object {
        const val UPLOAD_MIME_TYPE = "image/jpeg"
    }
}

/** The quality ladder, and the rule for choosing a rung. Pure so the choice is JVM-testable. */
internal object JpegLadder {
    val QUALITIES = listOf(90, 80, 70, 60)

    /**
     * Walks [QUALITIES] from the top and returns the first encode whose size, measured after the
     * metadata strip the upload applies, is at most [cap] (<= 0 means no cap).
     * [TooLarge][ChatMediaEncodingException.TooLarge] when something encoded but nothing fit;
     * [EncodingFailed][ChatMediaEncodingException.EncodingFailed] when nothing encoded at all.
     */
    fun select(cap: Long, encode: (quality: Int) -> ByteArray?): Result<ByteArray> {
        var encodedAny = false
        for (quality in QUALITIES) {
            val bytes = encode(quality)?.withoutJpegMetadata() ?: continue
            encodedAny = true
            if (cap <= 0 || bytes.size <= cap) return Result.success(bytes)
        }
        return Result.failure(
            if (encodedAny) ChatMediaEncodingException.TooLarge()
            else ChatMediaEncodingException.EncodingFailed()
        )
    }
}
