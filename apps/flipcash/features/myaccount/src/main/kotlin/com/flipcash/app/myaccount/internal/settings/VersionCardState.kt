package com.flipcash.app.myaccount.internal.settings

import com.flipcash.app.updates.OptionalUpdate

/** What the version card at the bottom of Settings shows. */
internal sealed interface VersionCardState {
    /** Pinned over the list while there is something to do: update, download, restart. */
    val pinned: Boolean get() = false

    /** A debug build: never pinned, no Update button. */
    data object Debug : VersionCardState

    data object UpToDate : VersionCardState

    /** [versionName] is null when the manifest can't name the build. */
    data class Available(val versionName: String?) : VersionCardState {
        override val pinned get() = true
    }

    data class Downloading(val versionName: String?, val fraction: Float) : VersionCardState {
        override val pinned get() = true
    }

    data class Ready(val versionName: String?) : VersionCardState {
        override val pinned get() = true
    }
}

/**
 * Debug comes first, as it does for the gear dot: a debug build never offers an update. Then the
 * furthest point of the flexible flow wins. The card leaves the pinned states only once the app has
 * restarted onto the new version, when [update] is gone, so it never jumps.
 */
internal fun versionCardState(
    isDebug: Boolean,
    update: OptionalUpdate?,
    downloaded: Boolean,
    progress: Float?,
): VersionCardState = when {
    isDebug -> VersionCardState.Debug
    downloaded -> VersionCardState.Ready(update?.versionName)
    update == null -> VersionCardState.UpToDate
    progress != null -> VersionCardState.Downloading(update.versionName, progress.coerceIn(0f, 1f))
    else -> VersionCardState.Available(update.versionName)
}
