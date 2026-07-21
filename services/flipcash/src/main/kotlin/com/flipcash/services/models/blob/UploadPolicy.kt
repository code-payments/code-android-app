package com.flipcash.services.models.blob

import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * The upload constraints the server enforces, surfaced so the client can filter selection and
 * resize/transcode BEFORE reserving an upload. Advisory and cacheable ([ttl] / [version]);
 * `initiateExternalUpload` remains authoritative.
 */
@Serializable
data class UploadPolicy(
    val version: String,
    val ttl: Duration,
    // Ordered most-specific-first: the first entry whose pattern matches wins.
    val mimeTypeConstraints: List<MimeTypeConstraints>,
) {
    /** The constraints governing [mimeType], or null if the type is not accepted. */
    fun constraintsFor(mimeType: String): MimeTypeConstraints? =
        mimeTypeConstraints.firstOrNull { it.matches(mimeType) }

    /** Whether [mimeType] is accepted for upload at all. */
    fun accepts(mimeType: String): Boolean = constraintsFor(mimeType) != null

    /** Concrete (non-wildcard) MIME types the policy explicitly names — useful for selection filters. */
    val explicitMimeTypes: List<String>
        get() = mimeTypeConstraints
            .map { it.mimeTypePattern }
            .filter { !it.contains('*') }
}

@Serializable
data class MimeTypeConstraints(
    // "image/jpeg" (exact), "image/*" (subtype wildcard), or "*/*" (catch-all).
    val mimeTypePattern: String,
    val maxSizeBytes: Long,
    val image: ImageConstraints?,
) {
    fun matches(mimeType: String): Boolean = when {
        mimeTypePattern == "*/*" -> true
        mimeTypePattern.endsWith("/*") ->
            mimeType.substringBefore('/').equals(mimeTypePattern.substringBefore('/'), ignoreCase = true)
        else -> mimeTypePattern.equals(mimeType, ignoreCase = true)
    }
}

@Serializable
data class ImageConstraints(
    val maxWidth: Int,
    val maxHeight: Int,
    val maxPixels: Long,
)
