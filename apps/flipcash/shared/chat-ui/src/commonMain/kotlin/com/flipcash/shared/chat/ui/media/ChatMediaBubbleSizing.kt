package com.flipcash.shared.chat.ui.media

/** The laid-out size of a photo bubble, in the unit [ChatMediaBubbleSizing.size] was given. */
data class ChatMediaBubbleSize(
    val width: Float,
    val height: Float,
    /** True when the photo's aspect ratio was clamped, so the center-crop drops part of it. */
    val cropped: Boolean,
)

/**
 * How tall a photo bubble is for a photo of a given shape. The same rule runs on iOS, pinned by the
 * `bubble` cases of `test-vectors/chat_media.json`.
 */
object ChatMediaBubbleSizing {
    /** The widest a photo may be relative to its height: height is never under half the width. */
    const val MIN_ASPECT = 0.5

    /**
     * The tallest a photo may be relative to its width: 5:4, as iMessage draws photos. A 3:4
     * portrait camera photo crops a little at the top and bottom, and a phone screenshot more; both
     * are whole in the viewer.
     */
    const val MAX_ASPECT = 1.25

    /**
     * The bubble is always [maxWidth] wide. Its height is `maxWidth × clamp(h / w, 0.5, 5/4)`; a
     * missing or zero dimension gives a square. [ChatMediaBubbleSize.cropped] says the clamp moved
     * the ratio. The ratio is compared in double, so a photo exactly 5:4 tall is not cropped.
     */
    fun size(imageWidth: Int?, imageHeight: Int?, maxWidth: Float): ChatMediaBubbleSize {
        val w = imageWidth ?: 0
        val h = imageHeight ?: 0
        if (w <= 0 || h <= 0) return ChatMediaBubbleSize(maxWidth, maxWidth, cropped = false)
        val ratio = h.toDouble() / w.toDouble()
        val clamped = ratio.coerceIn(MIN_ASPECT, MAX_ASPECT)
        return ChatMediaBubbleSize(maxWidth, (maxWidth * clamped).toFloat(), cropped = clamped != ratio)
    }
}
