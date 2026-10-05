package com.flipcash.shared.chat.ui.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.util.lerp

/**
 * Where the photo is drawn at one instant of the overlay's open, drag and close, in the overlay's
 * own coordinates.
 *
 * @property radiusPx corner radius of [rect].
 * @property contentAlpha opacity of the photo; below 1 only when there is no bubble to fly to or from.
 * @property backdrop opacity of the black behind it.
 * @property chromeAlpha opacity of the close and share buttons.
 */
data class ChatMediaFrame(
    val rect: Rect,
    val radiusPx: Float,
    val contentAlpha: Float,
    val backdrop: Float,
    val chromeAlpha: Float,
) {
    companion object {
        /** Nothing drawn: the overlay has no size yet. */
        val Hidden = ChatMediaFrame(Rect.Zero, 0f, 0f, 0f, 0f)
    }
}

/** The pure parts of the photo overlay: the pull's geometry, the release decision, the rect blend. */
internal object ChatPhotoTransition {
    /** A pull of this fraction of the container's height takes the shrink and the fade to their ends. */
    const val DRAG_FULL_FRACTION = 0.5f

    /** The scale a photo reaches at the end of a pull. */
    const val MIN_DRAG_SCALE = 0.6f

    /** The backdrop's opacity at the end of a pull: nearly the chat again, the way iOS's zoom dismissal clears. */
    const val MIN_DRAG_BACKDROP = 0.1f

    /** Letting go past this fraction of the container's height dismisses. */
    const val DISMISS_DISTANCE_FRACTION = 0.15f

    /** Letting go while moving down faster than this, in dp per second, dismisses. */
    const val DISMISS_VELOCITY_DP = 900f

    /** With no bubble to land in, the photo shrinks to this much of its size as it fades. */
    const val DETACHED_SCALE = 0.85f

    /** How far into the pull the finger is, 0 to 1. */
    fun dragProgress(pull: Offset, containerHeight: Float): Float {
        if (containerHeight <= 0f) return 0f
        return (pull.getDistance() / (containerHeight * DRAG_FULL_FRACTION)).coerceIn(0f, 1f)
    }

    fun dragScale(t: Float): Float = lerp(1f, MIN_DRAG_SCALE, t.coerceIn(0f, 1f))

    fun dragBackdrop(t: Float): Float = lerp(1f, MIN_DRAG_BACKDROP, t.coerceIn(0f, 1f))

    /** [open] shrunk about its center for a pull at progress [t], then moved with the finger by [pull]. */
    fun draggedRect(open: Rect, pull: Offset, t: Float): Rect = scaledAbout(open, dragScale(t)).translate(pull)

    fun scaledAbout(rect: Rect, scale: Float): Rect {
        val size = Size(rect.width * scale, rect.height * scale)
        return Rect(rect.center - Offset(size.width / 2f, size.height / 2f), size)
    }

    /** The rect a photo of [content] occupies fitted inside [container] at its origin, centered. */
    fun fittedRect(container: Size, content: Size): Rect {
        val size = ChatMediaZoom.fittedSize(container, content)
        return Rect(Offset((container.width - size.width) / 2f, (container.height - size.height) / 2f), size)
    }

    /**
     * Whether letting go of a pull dismisses: it was dragged far enough, or flung downward.
     * [velocity] is in px per second and [density] converts the dp threshold.
     */
    fun shouldDismiss(pull: Offset, velocity: Offset, containerHeight: Float, density: Float): Boolean {
        val far = pull.getDistance() > containerHeight * DISMISS_DISTANCE_FRACTION
        val flung = velocity.y > DISMISS_VELOCITY_DP * density && pull.y > 0f
        return far || flung
    }

    /** Whether a bubble at [bounds] is on screen enough to fly to or from. */
    fun sourceUsable(bounds: Rect, container: Size): Boolean {
        if (bounds.width <= 0f || bounds.height <= 0f) return false
        val visible = bounds.intersect(Rect(Offset.Zero, container))
        return visible.width > 0f && visible.height > 0f
    }

    /**
     * The frame at [progress]: 0 is the photo sitting on [source], 1 is [anchor], the open or
     * mid-pull rect. One progress drives both directions, so the target ([source] or [anchor]) may
     * move while it runs. With no [source] the photo grows from, and shrinks to, its own center while fading.
     *
     * @param anchorPull how far into a pull [anchor] is; the corners round and the backdrop and buttons fade with it.
     */
    fun frame(
        source: Rect?,
        sourceRadius: Float,
        anchor: Rect,
        anchorPull: Float,
        progress: Float,
    ): ChatMediaFrame {
        val p = progress.coerceIn(0f, 1f)
        val anchorRadius = if (source != null) sourceRadius * anchorPull else 0f
        val backdrop = dragBackdrop(anchorPull) * p
        val chrome = (1f - anchorPull) * p
        return if (source != null) {
            ChatMediaFrame(
                rect = lerp(source, anchor, p),
                radiusPx = lerp(sourceRadius, anchorRadius, p),
                contentAlpha = 1f,
                backdrop = backdrop,
                chromeAlpha = chrome,
            )
        } else {
            ChatMediaFrame(
                rect = lerp(scaledAbout(anchor, DETACHED_SCALE), anchor, p),
                radiusPx = anchorRadius,
                contentAlpha = p,
                backdrop = backdrop,
                chromeAlpha = chrome,
            )
        }
    }
}
