package com.flipcash.services.models.chat

import android.os.Parcelable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class BlobMetadata(
    val mimeType: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val image: ImageMetadata?,
    /**
     * When [downloadUrl] stops working, as epoch milliseconds — null for metadata that predates
     * this field (already-persisted rows) or a server that didn't send one.
     *
     * Every other field here is intrinsic to the immutable bytes; this one is not. The server
     * mints [downloadUrl] per fetch and expires it, so a metadata copy that outlives its expiry —
     * and these are persisted, see `user_profiles.profile_picture_json` — carries a URL that 403s.
     * Past this instant the id must be re-resolved through `GetBlobs` for a fresh URL.
     *
     * Stored as millis rather than an `Instant` so the type stays Parcelable and its persisted
     * JSON stays a primitive.
     */
    val expiresAtMillis: Long? = null,
): Parcelable {

    val expiresAt: Instant?
        get() = expiresAtMillis?.let { Instant.fromEpochMilliseconds(it) }

    /**
     * Whether [downloadUrl] is past — or within [margin] of — its expiry at [now], and so must be
     * re-minted before use. Metadata carrying no expiry is treated as usable: it either predates
     * the field or the server declined to bound it, and failing closed there would re-resolve
     * every blob on every load.
     */
    fun isDownloadUrlExpired(now: Instant, margin: Duration = EXPIRY_MARGIN): Boolean {
        val expiry = expiresAt ?: return false
        return now + margin >= expiry
    }

    companion object {
        /**
         * Treat a URL as already dead this far ahead of its stated expiry, so one that would die
         * mid-download doesn't produce a load error we'd have to recover from.
         */
        val EXPIRY_MARGIN: Duration = 30.seconds
    }
}
