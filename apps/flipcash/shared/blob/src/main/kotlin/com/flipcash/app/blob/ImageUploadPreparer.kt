package com.flipcash.app.blob

import android.net.Uri
import com.flipcash.services.models.blob.ImageConstraints
import com.flipcash.services.models.blob.UploadPolicy
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.uploadMimeFor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.sqrt

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
    private val contentReader: ContentReader,
) {
    sealed interface Outcome {
        /**
         * [uri] is a cached, re-encoded copy declaring [mimeType], ready for
         * [BlobStorageCoordinator.upload].
         *
         * The caller owns the file from here: nothing else deletes it, so it must be handed to
         * `ContentReader.removeFromCache` once uploaded or abandoned.
         */
        data class Prepared(val uri: Uri, val mimeType: String) : Outcome

        /** The policy names no constraints for what this file re-encodes to. */
        data object Unsupported : Outcome

        /** Even the smallest re-encode is over the byte ceiling. Pathological, but reachable. */
        data object TooLarge : Outcome

        /** The file could not be read or re-encoded at all. */
        data object Unreadable : Outcome
    }

    /**
     * Prepares [uri] for upload under [policy], writing the result into the cache under a name
     * derived from [fileNamePrefix].
     *
     * Blocking — decode and re-encode — so call it off the main dispatcher.
     *
     * A null [policy] means the launch preload has not landed yet. The pick is still re-encoded, at
     * [DEFAULT_MAX_EDGE], because the server rather than the cache is the authority on what it
     * accepts; refusing the pick because the advisory copy is missing would be worse than trying.
     */
    fun prepare(
        uri: Uri,
        policy: UploadPolicy?,
        fileNamePrefix: String,
    ): Outcome {
        val sourceMime = contentReader.mimeType(uri)
        // The cache re-encodes to JPEG/PNG, so gate on the type we would actually upload — not the
        // source type, which may normalize into an accepted format (e.g. HEIC -> PNG).
        val uploadMime = uploadMimeFor(sourceMime)
        val constraints = policy?.constraintsFor(uploadMime)
        if (policy != null && constraints == null) return Outcome.Unsupported

        val maxBytes = constraints?.maxSizeBytes
        val cached = cacheWithinPolicy(
            uri = uri,
            sourceMime = sourceMime,
            fileNamePrefix = fileNamePrefix,
            image = constraints?.image,
            maxBytes = maxBytes,
        ) ?: return Outcome.Unreadable

        if (maxBytes != null && (contentReader.size(cached) ?: 0L) > maxBytes) {
            contentReader.removeFromCache(cached)
            return Outcome.TooLarge
        }
        // The cache re-encodes (stripping EXIF); declare the type those bytes actually are.
        return Outcome.Prepared(uri = cached, mimeType = uploadMime)
    }

    /**
     * Re-encodes [uri] into the cache honouring [image]'s dimension caps, then shrinks the
     * longest-edge target until the output fits [maxBytes] — resizing to fit rather than rejecting.
     * Returns null only if re-encoding fails outright; otherwise the smallest attempt, which the
     * caller re-checks because a ceiling smaller than [MIN_MAX_EDGE] can produce is pathological.
     */
    private fun cacheWithinPolicy(
        uri: Uri,
        sourceMime: String?,
        fileNamePrefix: String,
        image: ImageConstraints?,
        maxBytes: Long?,
    ): Uri? {
        var edge = maxEdgeFor(image)
        var last: Uri? = null
        repeat(MAX_RESIZE_ATTEMPTS) {
            // Drop the previous over-ceiling attempt before making a smaller one.
            last?.let { contentReader.removeFromCache(it) }
            val candidate = contentReader.copyToCache(
                uri = uri,
                fileName = "${fileNamePrefix}_${System.nanoTime()}",
                maxSize = edge,
                mimeType = sourceMime,
            ) ?: return null
            last = candidate
            val size = contentReader.size(candidate) ?: 0L
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
