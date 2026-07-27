package com.flipcash.services.models.chat

import com.getcode.utils.base58
import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    val renditions: List<MediaItemRendition>,
) {
    /**
     * The [preferred] rendition if it's available, otherwise the next lower-quality rendition
     * that is — degrading down the quality ladder ORIGINAL → DISPLAY → THUMBNAIL. Returns
     * null when no rendition at or below [preferred] is available.
     *
     * "Available" means the rendition's [MediaItemRendition.blob] is populated (it has a
     * download URL); renditions still uploading are skipped so the fallback keeps degrading.
     * This never upgrades above [preferred] — e.g. `rendition(Role.THUMBNAIL)` will not
     * return the ORIGINAL.
     */
    fun rendition(preferred: MediaItemRendition.Role = MediaItemRendition.Role.ORIGINAL): MediaItemRendition? {
        val start = QUALITY_LADDER.indexOf(preferred)
        if (start < 0) return null // UNKNOWN / non-ladder roles have no fallback chain
        for (i in start until QUALITY_LADDER.size) {
            renditions.firstOrNull { it.role == QUALITY_LADDER[i] && it.blob != null }
                ?.let { return it }
        }
        return null
    }

    /** Download URL of the rendition resolved for [preferred], or null if none is available. */
    fun url(preferred: MediaItemRendition.Role = MediaItemRendition.Role.ORIGINAL): String? =
        rendition(preferred)?.blob?.downloadUrl

    /**
     * The most appropriate rendition to display at [targetLongestSidePx] pixels — the *smallest*
     * available rendition whose longest side is at least the target, so a surface gets enough
     * pixels to look crisp without over-fetching. Falls back to the largest available rendition
     * when none is big enough.
     *
     * The server derives several sizes per role (e.g. THUMBNAIL at 32/160/320, DISPLAY at
     * 800/1600), so picking by [MediaItemRendition.Role] alone is ambiguous — a 32px thumbnail
     * looks grainy on a 96px avatar. This selects by pixel size instead. The full-quality
     * [MediaItemRendition.Role.ORIGINAL] upload is excluded (it's arbitrarily large); only
     * available renditions (populated blob) that carry image dimensions are considered. When none
     * do, this degrades to the role-based [rendition] ladder (capped at DISPLAY to avoid ORIGINAL).
     */
    fun renditionForSize(targetLongestSidePx: Int): MediaItemRendition? {
        val sized = sizedRenditions()
        if (sized.isEmpty()) return rendition(MediaItemRendition.Role.DISPLAY)
        val ranked = sized.sortedBy { it.second }
        return (ranked.firstOrNull { it.second >= targetLongestSidePx } ?: ranked.last()).first
    }

    /** Download URL of the rendition resolved for [renditionForSize], or null if none exists. */
    fun urlForSize(targetLongestSidePx: Int): String? =
        renditionForSize(targetLongestSidePx)?.blob?.downloadUrl

    /**
     * Stable Coil cache key for the rendition [renditionForSize] resolves to, derived from the
     * durable [MediaItemRendition.blobId] rather than the download URL. The server re-mints and
     * expires `download_url` on every fetch, so keying the cache on the URL guarantees a miss on
     * the next fetch even though the bytes are immutable — the blob id is the rendition's stable
     * identity. Null exactly when [urlForSize] is null.
     */
    fun cacheKeyForSize(targetLongestSidePx: Int): String? =
        renditionForSize(targetLongestSidePx)?.cacheKey

    /** Stable cache key for [renditionBelow], for use as a placeholder memory-cache key. */
    fun cacheKeyBelow(targetLongestSidePx: Int): String? =
        renditionBelow(targetLongestSidePx)?.cacheKey

    /**
     * The largest available rendition strictly smaller than [targetLongestSidePx], or null if
     * there isn't one. This is the best intermediate placeholder to show while [renditionForSize]
     * loads: e.g. an info card targeting 320 reuses the 160 the list already cached, upgrading in
     * place rather than flashing.
     */
    fun renditionBelow(targetLongestSidePx: Int): MediaItemRendition? =
        sizedRenditions()
            .filter { it.second < targetLongestSidePx }
            .maxByOrNull { it.second }
            ?.first

    /**
     * Any available BlurHash. All renditions describe the same image, so any one's hash is an
     * acceptable instant preview while a full rendition downloads. Null when none carry one.
     */
    fun blurhash(): String? =
        renditions.firstNotNullOfOrNull { it.blob?.image?.blurhash?.takeIf(String::isNotEmpty) }

    /** Available, non-ORIGINAL renditions paired with their longest side, in list order. */
    private fun sizedRenditions(): List<Pair<MediaItemRendition, Int>> =
        renditions
            .filter { it.role != MediaItemRendition.Role.ORIGINAL }
            .mapNotNull { r -> r.longestSide?.let { r to it } }

    companion object {
        /** Rendition quality, best first; fallback walks this downward from the requested role. */
        private val QUALITY_LADDER = listOf(
            MediaItemRendition.Role.ORIGINAL,
            MediaItemRendition.Role.DISPLAY,
            MediaItemRendition.Role.THUMBNAIL,
        )

        /** Longest image side (px) of a rendition, or null if it has no image dimensions. */
        private val MediaItemRendition.longestSide: Int?
            get() = blob?.image?.let { maxOf(it.width, it.height) }

        /** Stable, URL-independent cache key for a rendition — its durable blob id, base58-encoded. */
        private val MediaItemRendition.cacheKey: String
            get() = blobId.bytes.base58
    }
}
