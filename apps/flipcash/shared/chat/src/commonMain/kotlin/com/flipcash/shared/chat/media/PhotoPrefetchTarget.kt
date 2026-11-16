package com.flipcash.shared.chat.media

import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition

/**
 * Which of a photo's renditions its transcript bubble will request on this device. A prefetch has to
 * download that blob and no other: the disk cache is keyed by blob id, so any other is wasted.
 */
fun interface PhotoPrefetchTarget {
    /** [groupIncoming] is a photo from someone else in a group, drawn beside the avatar column. */
    fun rendition(item: MediaItem, groupIncoming: Boolean): MediaItemRendition?
}
