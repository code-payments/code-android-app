package com.flipcash.app.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.MediaItem

val LocalMediaUrlResolver = staticCompositionLocalOf<MediaUrlResolver?> { null }

/**
 * The download URL a blob-backed surface should render, and whether rendering it is hopeless.
 *
 * [url] starts as the URL stored on the [MediaItem] and is replaced if it needs re-minting, so a
 * cached rendition draws on the first frame instead of waiting on a resolve. [hasFailed] only goes
 * true once a re-minted URL has failed too — a first failure is treated as a stale URL and retried
 * silently, leaving whatever placeholder is on screen in place.
 */
@Stable
class MediaUrlState internal constructor(initialUrl: String?) {
    var url: String? by mutableStateOf(initialUrl)
        internal set

    var hasFailed: Boolean by mutableStateOf(false)
        internal set

    internal var failures by mutableIntStateOf(0)
        private set

    /** The URL the last failure was reported against — what a re-mint has to improve on. */
    internal var failedUrl: String? = null
        private set

    /** Report that the image loader could not load [url]. */
    fun onLoadFailed() {
        failedUrl = url
        failures++
    }
}

/**
 * Resolves [media]'s rendition for [targetLongestSidePx] into a URL that can be handed to the image
 * loader, keeping it usable as the stored one expires. See [MediaUrlResolver] for why a stored URL
 * often isn't. [access] names the surface [media] is being read from — a re-mint of media the
 * caller doesn't own resolves to nothing without it.
 */
@Composable
fun rememberMediaUrl(
    media: MediaItem?,
    targetLongestSidePx: Int,
    access: BlobAccessContext,
): MediaUrlState {
    val resolver = LocalMediaUrlResolver.current
    val state = remember(media, targetLongestSidePx) {
        MediaUrlState(media?.urlForSize(targetLongestSidePx))
    }

    LaunchedEffect(state, resolver) {
        if (media == null || resolver == null) return@LaunchedEffect
        resolver.urlForSize(media, targetLongestSidePx, access)?.let { state.url = it }
    }

    LaunchedEffect(state, resolver, state.failures) {
        if (state.failures == 0) return@LaunchedEffect
        // Only the first failure is worth a re-mint; a fresh URL that also fails is a real error.
        if (media == null || resolver == null || state.failures > 1) {
            state.hasFailed = true
            return@LaunchedEffect
        }
        val fresh = resolver.refreshUrlForSize(media, targetLongestSidePx, state.failedUrl, access)
        if (fresh != null && fresh != state.url) {
            state.url = fresh
        } else {
            state.hasFailed = true
        }
    }

    return state
}
