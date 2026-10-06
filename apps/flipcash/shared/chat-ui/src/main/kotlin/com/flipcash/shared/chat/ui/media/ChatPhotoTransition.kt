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
 * @property corners corner radii of [rect].
 * @property contentAlpha opacity of the photo; below 1 only when there is no bubble to fly to or from.
 * @property backdrop opacity of the black behind it.
 * @property chromeAlpha opacity of the close and share buttons.
 * @property clip where the overlay may draw the photo; null for anywhere.
 * @property handoff how much of the photo the transcript draws instead of the overlay, from 0 to 1.
 * The transcript's copy sits under the bars' blur and fade the way the bubble does, so the photo
 * lands looking like the bubble rather than changing as the overlay goes.
 */
data class ChatMediaFrame(
    val rect: Rect,
    val corners: ChatPhotoCorners,
    val contentAlpha: Float,
    val backdrop: Float,
    val chromeAlpha: Float,
    val clip: Rect? = null,
    val handoff: Float = 0f,
) {
    companion object {
        /** Nothing drawn: the overlay has no size yet. */
        val Hidden = ChatMediaFrame(Rect.Zero, ChatPhotoCorners.uniform(0f), 0f, 0f, 0f)
    }
}

/** A photo's corner radii in px, start and end following the layout direction like [androidx.compose.foundation.shape.RoundedCornerShape]. */
data class ChatPhotoCorners(
    val topStart: Float,
    val topEnd: Float,
    val bottomEnd: Float,
    val bottomStart: Float,
) {
    val max: Float get() = maxOf(maxOf(topStart, topEnd), maxOf(bottomEnd, bottomStart))

    val isSquare: Boolean get() = max <= 0f

    companion object {
        fun uniform(radius: Float) = ChatPhotoCorners(radius, radius, radius, radius)
    }
}

internal fun lerp(start: ChatPhotoCorners, stop: ChatPhotoCorners, fraction: Float) = ChatPhotoCorners(
    topStart = lerp(start.topStart, stop.topStart, fraction),
    topEnd = lerp(start.topEnd, stop.topEnd, fraction),
    bottomEnd = lerp(start.bottomEnd, stop.bottomEnd, fraction),
    bottomStart = lerp(start.bottomStart, stop.bottomStart, fraction),
)

/** The pure parts of the photo overlay: the pull's geometry, the release decision, the rect blend. */
internal object ChatPhotoTransition {
    /**
     * The last stretch of the open over which the clip widens from the transcript to the screen.
     * Spread over the whole open, the clip trails a photo that crosses the bars early in its
     * flight, and the photo is drawn over them until it nearly lands.
     */
    private const val CLIP_RELEASE = 0.2f

    private fun clipProgress(p: Float) = (1f - (1f - p) / CLIP_RELEASE).coerceIn(0f, 1f)

    /**
     * The first stretch of the open over which the transcript hands the photo to the overlay, and
     * the last of the close over which it takes it back. Short, since the transcript's copy is
     * under the backdrop, which is near clear only here.
     */
    private const val HANDOFF = 0.2f

    private fun handoff(p: Float) = (1f - p / HANDOFF).coerceIn(0f, 1f)

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
     * @param sourceCorners the bubble's own corners, which can differ where it joins a run.
     * @param anchorPull how far into a pull [anchor] is; the corners round and the backdrop and buttons fade with it.
     * @param transcript where the chat is not covered by its bars. The photo is clipped to it on
     * the bubble, the way the bubble is, and to the whole [container] when open. Null clips nothing.
     */
    fun frame(
        source: Rect?,
        sourceCorners: ChatPhotoCorners,
        anchor: Rect,
        anchorPull: Float,
        progress: Float,
        transcript: Rect? = null,
        container: Size = Size.Zero,
    ): ChatMediaFrame {
        val p = progress.coerceIn(0f, 1f)
        val anchorCorners = ChatPhotoCorners.uniform(if (source != null) sourceCorners.max * anchorPull else 0f)
        val backdrop = dragBackdrop(anchorPull) * p
        val chrome = (1f - anchorPull) * p
        return if (source != null) {
            ChatMediaFrame(
                rect = lerp(source, anchor, p),
                corners = lerp(sourceCorners, anchorCorners, p),
                contentAlpha = 1f,
                backdrop = backdrop,
                chromeAlpha = chrome,
                clip = transcript?.let { lerp(it, Rect(Offset.Zero, container), clipProgress(p)) },
                handoff = if (transcript != null) handoff(p) else 0f,
            )
        } else {
            ChatMediaFrame(
                rect = lerp(scaledAbout(anchor, DETACHED_SCALE), anchor, p),
                corners = anchorCorners,
                contentAlpha = p,
                backdrop = backdrop,
                chromeAlpha = chrome,
            )
        }
    }
}
