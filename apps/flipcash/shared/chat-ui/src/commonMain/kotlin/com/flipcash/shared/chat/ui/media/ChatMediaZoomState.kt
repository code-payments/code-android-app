package com.flipcash.shared.chat.ui.media

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import kotlin.math.max

/** The zoom arithmetic of the viewer, free of Compose state so it can be tested on the JVM. */
internal object ChatMediaZoom {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f
    const val DOUBLE_TAP_SCALE = 2.5f

    /** Scales under this count as "fit": the swipe-down dismiss is live. */
    private const val FIT_EPSILON = 0.01f

    fun isFit(scale: Float): Boolean = scale <= MIN_SCALE + FIT_EPSILON

    /**
     * The size [content] occupies once fitted inside [container], preserving aspect. An unknown
     * content size fits to the whole container.
     */
    fun fittedSize(container: Size, content: Size): Size {
        if (content.width <= 0f || content.height <= 0f || !content.width.isFinite() || !content.height.isFinite()) {
            return container
        }
        val factor = minOf(container.width / content.width, container.height / content.height)
        return Size(content.width * factor, content.height * factor)
    }

    /**
     * Limits [offset] so a [fitted] image scaled by [scale] and centered in [container] never
     * leaves a gap between its edge and the container's. On an axis where the scaled image is
     * smaller than the container, the image stays centered.
     */
    fun clampOffset(offset: Offset, scale: Float, container: Size, fitted: Size): Offset {
        val maxX = max(0f, (fitted.width * scale - container.width) / 2f)
        val maxY = max(0f, (fitted.height * scale - container.height) / 2f)
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    /**
     * The offset that keeps the image point under [focus] (container coordinates) where it is
     * while the scale goes from [oldScale] to [newScale], then adds [pan].
     */
    fun offsetAfterZoom(
        offset: Offset,
        focus: Offset,
        oldScale: Float,
        newScale: Float,
        pan: Offset,
        container: Size,
    ): Offset {
        val center = Offset(container.width / 2f, container.height / 2f)
        val ratio = newScale / oldScale
        val fromCenter = focus - center
        return fromCenter - (fromCenter - offset) * ratio + pan
    }
}

/**
 * The viewer's zoom and pan. Hoisted so the host can reset it and tests can read it.
 *
 * [scale] runs 1 to 4. [offset] is the translation of the scaled image, in pixels, already clamped
 * so the image's edges never pull inside the container.
 */
@Stable
class ChatMediaZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Whether the image is magnified past fit. Swipe-down dismiss only applies when it is not. */
    val isZoomed: Boolean get() = !ChatMediaZoom.isFit(scale)

    internal var container: Size = Size.Zero
    internal var content: Size = Size.Zero

    private val fitted: Size get() = ChatMediaZoom.fittedSize(container, content)

    /** One step of a pinch or pan: [zoom] is the factor since the last event, [pan] its movement. */
    internal fun transform(focus: Offset, pan: Offset, zoom: Float) {
        // The event that lifts the last finger has no centroid; its NaN would poison the offset.
        if (!focus.isSpecified) return
        val newScale = (scale * zoom).coerceIn(ChatMediaZoom.MIN_SCALE, ChatMediaZoom.MAX_SCALE)
        val moved = ChatMediaZoom.offsetAfterZoom(offset, focus, scale, newScale, pan, container)
        scale = newScale
        offset = ChatMediaZoom.clampOffset(moved, newScale, container, fitted)
    }

    /** Back to fit. */
    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /**
     * Double-tap: from fit, to [ChatMediaZoom.DOUBLE_TAP_SCALE] with [focus] held still; from any
     * zoom, back to fit. Snaps when [animated] is false.
     */
    internal suspend fun toggle(focus: Offset, animated: Boolean) {
        val startScale = scale
        val startOffset = offset
        val zoomingIn = !isZoomed
        val endScale = if (zoomingIn) ChatMediaZoom.DOUBLE_TAP_SCALE else 1f
        val endOffset = if (zoomingIn) {
            ChatMediaZoom.clampOffset(
                ChatMediaZoom.offsetAfterZoom(
                    offset, focus, startScale, endScale, Offset.Zero, container,
                ),
                endScale, container, fitted,
            )
        } else {
            Offset.Zero
        }
        if (!animated) {
            scale = endScale
            offset = endOffset
            return
        }
        animate(0f, 1f, animationSpec = tween(250)) { t, _ ->
            scale = startScale + (endScale - startScale) * t
            offset = startOffset + (endOffset - startOffset) * t
        }
        scale = endScale
        offset = endOffset
    }
}
