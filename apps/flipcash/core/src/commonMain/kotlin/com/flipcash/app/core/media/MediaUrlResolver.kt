package com.flipcash.app.core.media

import com.flipcash.app.core.time.TimeProvider
import com.flipcash.services.controllers.BlobStorageController
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands out download URLs for [MediaItem] renditions that are actually usable.
 *
 * A blob's bytes are immutable but its `download_url` is a short-lived signed URL carrying an
 * expiry; the client is expected to call `GetBlobs` for a fresh one once that passes. Because the
 * whole [MediaItem] is persisted (profile pictures live in the `user_profiles` table), a stored URL
 * is routinely older than its expiry by the time a surface asks for it, and handing that straight
 * to the image loader produces a failed load and a placeholder.
 *
 * Two things stop this from turning into a request per avatar:
 *  - renditions whose bytes are already cached are left alone, since their URL is never fetched, and
 *  - re-mints are memoised for the process lifetime and serialised, so a screenful of surfaces
 *    sharing a rendition costs one call.
 *
 * Every entry point takes the [BlobAccessContext] the media is being read from, because that is
 * what authorizes a re-mint of a blob the caller doesn't own — another user's avatar resolves to
 * nothing without it.
 *
 * [refreshUrlForSize] is the recovery path for a URL that failed anyway — metadata persisted before
 * expiry was modelled carries no expiry at all, so it can only be found stale by trying it.
 */
@Singleton
class MediaUrlResolver @Inject constructor(
    private val blobStorage: BlobStorageController,
    private val cache: ImageCachePresence,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()
    private val reminted = mutableMapOf<String, BlobMetadata>()

    /**
     * A usable download URL for the rendition [MediaItem.renditionForSize] picks for
     * [targetLongestSidePx], re-minting first if the stored one has expired. Null when the item has
     * no rendition for that size. Falls back to the stored URL if a re-mint fails, so an offline
     * client still gets whatever chance the cache gives it.
     */
    suspend fun urlForSize(
        media: MediaItem,
        targetLongestSidePx: Int,
        access: BlobAccessContext,
    ): String? {
        val rendition = media.renditionForSize(targetLongestSidePx) ?: return null
        val stored = rendition.blob ?: return null

        reminted[rendition.cacheKey]?.let { return it.downloadUrl }
        if (!stored.isDownloadUrlExpired(time.now())) return stored.downloadUrl
        if (cache.holds(rendition.cacheKey)) return stored.downloadUrl

        return remint(rendition, access, staleUrl = stored.downloadUrl)?.downloadUrl
            ?: stored.downloadUrl
    }

    /**
     * Re-mints the rendition for [targetLongestSidePx] after [failedUrl] failed to load, returning
     * a URL to try instead, or null if the blob could not be resolved. This is the only signal
     * available for metadata that carries no expiry — every row persisted before expiry was
     * modelled — so a failure is taken to mean the URL was stale.
     *
     * Passing the URL that actually failed is what keeps this from duplicating work: if another
     * caller has already minted a URL past that one, it is returned as-is.
     */
    suspend fun refreshUrlForSize(
        media: MediaItem,
        targetLongestSidePx: Int,
        failedUrl: String?,
        access: BlobAccessContext,
    ): String? {
        val rendition = media.renditionForSize(targetLongestSidePx) ?: return null
        return remint(rendition, access, staleUrl = failedUrl)?.downloadUrl
    }

    /** Drops memoised URLs — they are minted for the signed-in owner and don't outlive the session. */
    suspend fun reset() {
        mutex.withLock { reminted.clear() }
    }

    // Serialised so a screenful of surfaces resolving the same rendition makes one call, not one
    // each. Callers that were already waiting take whatever the winner minted, as long as it isn't
    // the [staleUrl] they came in holding.
    private suspend fun remint(
        rendition: MediaItemRendition,
        access: BlobAccessContext,
        staleUrl: String?,
    ): BlobMetadata? = mutex.withLock {
        reminted[rendition.cacheKey]
            ?.takeIf { it.downloadUrl != staleUrl }
            ?.let { return@withLock it }

        blobStorage.refreshMetadata(listOf(rendition.blobId), access)
            .getOrNull()
            ?.get(rendition.cacheKey)
            ?.also { reminted[rendition.cacheKey] = it }
    }
}
