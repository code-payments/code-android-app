package com.flipcash.shared.chat.ui.media

import android.content.Context
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.shared.chat.media.PhotoPrefetchTarget
import com.flipcash.shared.chat.ui.photoBubbleWidthPx

/** Picks the rendition the transcript bubble will request, from this device's display. */
class BubblePhotoPrefetchTarget(private val context: Context) : PhotoPrefetchTarget {
    override fun rendition(item: MediaItem, groupIncoming: Boolean): MediaItemRendition? {
        val metrics = context.resources.displayMetrics
        // The app runs in portrait only, so the transcript spans the display's shorter side however
        // the device is held when the photo arrives.
        val screenWidthPx = minOf(metrics.widthPixels, metrics.heightPixels)
        return bubblePhotoRendition(item, photoBubbleWidthPx(screenWidthPx, metrics.density, groupIncoming))
    }
}
