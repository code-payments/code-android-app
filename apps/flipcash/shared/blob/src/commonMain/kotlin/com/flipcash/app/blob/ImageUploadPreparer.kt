package com.flipcash.app.blob

import com.flipcash.services.models.blob.ImageConstraints
import com.flipcash.services.models.blob.UploadPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * What re-encoding a picked image for upload needs from the platform: reading its type, writing the
 * downscaled copy into the cache, and measuring it. Android answers with `ContentResolver` and
 * `Bitmap`, desktop with files and Skia.
 */
interface UploadImageCache {
    /** The content MIME type of [photo] (e.g. "image/jpeg"), or null if it can't be resolved. */
    fun mimeType(photo: PickedPhoto): String?

    /** The MIME type [copyToCache] bytes will actually be for a source of [sourceMimeType]: JPEG stays JPEG, everything else is PNG. */
    fun uploadMimeFor(sourceMimeType: String?): String

    /**
     * Re-encodes [photo] into the cache as [fileName], downscaled to [maxSize] on its longer side
     * and stripped of EXIF metadata. [mimeType] (the source type) picks the output format.
     */
    fun copyToCache(photo: PickedPhoto, fileName: String, maxSize: Int, mimeType: String?): PickedPhoto?

    fun removeFromCache(photo: PickedPhoto)

    /** The size of [photo]'s content in bytes, or null if it can't be resolved. */
    fun size(photo: PickedPhoto): Long?
}

/**
 * Turns a picked image into bytes the server will accept, or says why it cannot.
 *
 * The work is the same wherever the app uploads a picture — a profile photo, a group avatar — and
 * it is not a one-liner: the picked file has to be re-encoded to a type the policy names, then
 * shrunk until it fits that type's byte ceiling. Two copies of that loop would be two chances to
 * get the convergence wrong, and the failure is silent (an upload the server refuses) rather than a
 * crash, so it sits here once.
 *
 * What it does *not* decide is what the user is told. [Outcome.Unsupported] and [Outcome.TooLarge]
 * are the two refusals a caller must render, and the wording belongs to the screen the pick came
 * from — a group avatar and a profile photo do not say the same thing about the same file.
 */
@Singleton
class ImageUploadPreparer @Inject constructor(
    private val cache: UploadImageCache,
    /** How a platform location (an Android `Uri`) becomes the [PickedPhoto] [prepare] takes. */
    val photos: PickedPhotos,
) {
    sealed interface Outcome {
        /**
         * [photo] is a cached, re-encoded copy declaring [mimeType], ready for
         * [BlobStorageCoordinator.upload].
         *
         * The caller owns the file from here: nothing else deletes it, so it must be handed to
         * [UploadImageCache.removeFromCache] once uploaded or abandoned.
         */
        data class Prepared(val photo: PickedPhoto, val mimeType: String) : Outcome

        /** The policy names no constraints for what this file re-encodes to. */
        data object Unsupported : Outcome

        /** Even the smallest re-encode is over the byte ceiling. Pathological, but reachable. */
        data object TooLarge : Outcome

        /** The file could not be read or re-encoded at all. */
        data object Unreadable : Outcome
    }

    /**
     * Prepares [photo] for upload under [policy], writing the result into the cache under a name
     * derived from [fileNamePrefix].
     *
     * Blocking — decode and re-encode — so call it off the main dispatcher.
     *
     * A null [policy] means the launch preload has not landed yet. The pick is still re-encoded, at
     * [DEFAULT_MAX_EDGE], because the server rather than the cache is the authority on what it
     * accepts; refusing the pick because the advisory copy is missing would be worse than trying.
     */
    fun prepare(
        photo: PickedPhoto,
        policy: UploadPolicy?,
        fileNamePrefix: String,
    ): Outcome {
        val sourceMime = cache.mimeType(photo)
        // The cache re-encodes to JPEG/PNG, so gate on the type we would actually upload — not the
        // source type, which may normalize into an accepted format (e.g. HEIC -> PNG).
        val uploadMime = cache.uploadMimeFor(sourceMime)
        val constraints = policy?.constraintsFor(uploadMime)
        if (policy != null && constraints == null) return Outcome.Unsupported

        val maxBytes = constraints?.maxSizeBytes
        val cached = cacheWithinPolicy(
            photo = photo,
            sourceMime = sourceMime,
            fileNamePrefix = fileNamePrefix,
            image = constraints?.image,
            maxBytes = maxBytes,
        ) ?: return Outcome.Unreadable

        if (maxBytes != null && (cache.size(cached) ?: 0L) > maxBytes) {
            cache.removeFromCache(cached)
            return Outcome.TooLarge
        }
        // The cache re-encodes (stripping EXIF); declare the type those bytes actually are.
        return Outcome.Prepared(photo = cached, mimeType = uploadMime)
    }

    /**
     * Re-encodes [photo] into the cache honouring [image]'s dimension caps, then shrinks the
     * longest-edge target until the output fits [maxBytes] — resizing to fit rather than rejecting.
     * Returns null only if re-encoding fails outright; otherwise the smallest attempt, which the
     * caller re-checks because a ceiling smaller than [MIN_MAX_EDGE] can produce is pathological.
     */
    private fun cacheWithinPolicy(
        photo: PickedPhoto,
        sourceMime: String?,
        fileNamePrefix: String,
        image: ImageConstraints?,
        maxBytes: Long?,
    ): PickedPhoto? {
        var edge = maxEdgeFor(image)
        var last: PickedPhoto? = null
        repeat(MAX_RESIZE_ATTEMPTS) {
            // Drop the previous over-ceiling attempt before making a smaller one.
            last?.let { cache.removeFromCache(it) }
            val candidate = cache.copyToCache(
                photo = photo,
                fileName = "${fileNamePrefix}_${System.nanoTime()}",
                maxSize = edge,
                mimeType = sourceMime,
            ) ?: return null
            last = candidate
            val size = cache.size(candidate) ?: 0L
            if (maxBytes == null || size <= maxBytes) return candidate
            // Encoded bytes track pixel area (edge squared); scale the edge by sqrt(ceiling/actual)
            // with a safety margin to converge, floored at MIN_MAX_EDGE.
            val next = floor(edge * sqrt(maxBytes.toDouble() / size) * RESIZE_SAFETY)
                .toInt()
                .coerceAtLeast(MIN_MAX_EDGE)
            if (next >= edge) return candidate // already at the floor — hand back the best effort
            edge = next
        }
        return last
    }

    /**
     * The longest-edge cap that satisfies every dimension constraint in [image]: the smallest of
     * maxWidth, maxHeight, and sqrt(maxPixels) — bounding the longest edge by sqrt(maxPixels) keeps
     * total area within maxPixels. Falls back to [DEFAULT_MAX_EDGE] when the policy names no image
     * constraints.
     */
    private fun maxEdgeFor(image: ImageConstraints?): Int {
        val caps = listOfNotNull(
            image?.maxWidth,
            image?.maxHeight,
            image?.maxPixels?.let { floor(sqrt(it.toDouble())).toInt() },
        ).filter { it > 0 }
        return caps.minOrNull() ?: DEFAULT_MAX_EDGE
    }

    private companion object {
        // Longest-edge downscale target used when the upload policy specifies no dimension caps.
        const val DEFAULT_MAX_EDGE = 500

        // Floor for the resize-to-fit loop — below this an avatar is no longer worth keeping.
        const val MIN_MAX_EDGE = 64

        // How many times to shrink-and-retry before handing back the smallest attempt.
        const val MAX_RESIZE_ATTEMPTS = 5

        // Under-shoot the estimated fitting edge so re-encode overhead doesn't push us back over.
        const val RESIZE_SAFETY = 0.9
    }
}
