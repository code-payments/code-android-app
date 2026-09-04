package com.flipcash.services.models.chat

import android.os.Parcelable
import com.getcode.utils.base58
import kotlin.time.Instant
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class MediaItemRendition(
    val role: Role,
    val blobId: BlobId,
    val blob: BlobMetadata?,
): Parcelable {
    enum class Role {
        UNKNOWN,
        ORIGINAL,
        DISPLAY,
        THUMBNAIL,
    }

    /**
     * Stable, URL-independent image-cache key — the durable blob id, base58-encoded. The bytes a
     * blob id addresses never change, while `download_url` is re-minted per fetch, so keying a
     * cache on the URL misses every time even though the bytes are already held.
     */
    // Computed, not a stored property: a backing field here would be written by both Parcelize
    // and the persisted JSON for a value fully derived from blobId.
    val cacheKey: String
        get() = blobId.bytes.base58

    /** Whether this rendition's download URL needs re-minting before use at [now]. */
    fun isDownloadUrlExpired(now: Instant): Boolean = blob?.isDownloadUrlExpired(now) ?: false
}
