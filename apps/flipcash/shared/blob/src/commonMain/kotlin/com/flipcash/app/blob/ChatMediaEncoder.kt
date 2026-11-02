package com.flipcash.app.blob

import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.internal.extensions.withoutJpegMetadata
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a picked or captured photo into the JPEG a chat message uploads: decoded with its EXIF
 * rotation applied, scaled under the policy's bounds, flattened onto white, and compressed at the
 * highest quality that fits the byte cap.
 *
 * The result is always `image/jpeg`. There is no fallback to another type and, for a sealed
 * upload, none to plaintext, so a photo that can't be made to fit is refused.
 */
@Singleton
class ChatMediaEncoder @Inject constructor(
    private val codec: ImageCodec,
    private val dispatchers: DispatcherProvider,
) {
    /** [bytes] are metadata-free; [width] x [height] are display-space pixels. */
    class Encoded(val bytes: ByteArray, val width: Int, val height: Int)

    suspend fun encode(photo: PickedPhoto, limits: ChatMediaLimits): Result<Encoded> = withContext(dispatchers.IO) {
        val source = codec.decode(photo, limits)
            .getOrElse { return@withContext Result.failure(ChatMediaEncodingException.EncodingFailed(it)) }
        source.use {
            JpegLadder.select(cap = limits.maxBytes) { quality -> source.compressJpeg(quality) }
                .map { Encoded(it, source.width, source.height) }
        }
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
