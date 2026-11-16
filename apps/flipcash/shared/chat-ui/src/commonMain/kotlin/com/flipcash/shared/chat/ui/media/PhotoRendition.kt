package com.flipcash.shared.chat.ui.media

import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition

/**
 * The rendition a photo drawn [targetPx] on its longest side loads: the smallest derived size that
 * covers it, or the original when the server derived none (a sealed photo, or one still being
 * processed). The photo bubble and the quote thumbnail both pick through this.
 */
internal fun photoRendition(item: MediaItem, targetPx: Int): MediaItemRendition? =
    item.renditionForSize(targetPx) ?: item.rendition(MediaItemRendition.Role.ORIGINAL)
