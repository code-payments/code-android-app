package com.flipcash.services.models.chat

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
    fun rendition(preferred: MediaItemRendition.Role): MediaItemRendition? {
        val start = QUALITY_LADDER.indexOf(preferred)
        if (start < 0) return null // UNKNOWN / non-ladder roles have no fallback chain
        for (i in start until QUALITY_LADDER.size) {
            renditions.firstOrNull { it.role == QUALITY_LADDER[i] && it.blob != null }
                ?.let { return it }
        }
        return null
    }

    /** Download URL of the rendition resolved for [preferred], or null if none is available. */
    fun url(preferred: MediaItemRendition.Role): String? =
        rendition(preferred)?.blob?.downloadUrl

    companion object {
        /** Rendition quality, best first; fallback walks this downward from the requested role. */
        private val QUALITY_LADDER = listOf(
            MediaItemRendition.Role.ORIGINAL,
            MediaItemRendition.Role.DISPLAY,
            MediaItemRendition.Role.THUMBNAIL,
        )
    }
}
