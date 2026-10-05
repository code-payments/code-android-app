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
    const val MIN_ASPECT = 0.5f

    /** The tallest a photo may be relative to its width: height is never over twice the width. */
    const val MAX_ASPECT = 2.0f

    /**
     * The bubble is always [maxWidth] wide. Its height is `maxWidth × clamp(h / w, 0.5, 2.0)`; a
     * missing or zero dimension gives a square. [ChatMediaBubbleSize.cropped] says the clamp moved
     * the ratio.
     */
    fun size(imageWidth: Int?, imageHeight: Int?, maxWidth: Float): ChatMediaBubbleSize {
        val w = imageWidth ?: 0
        val h = imageHeight ?: 0
        if (w <= 0 || h <= 0) return ChatMediaBubbleSize(maxWidth, maxWidth, cropped = false)
        val ratio = h.toFloat() / w.toFloat()
        val clamped = ratio.coerceIn(MIN_ASPECT, MAX_ASPECT)
        return ChatMediaBubbleSize(maxWidth, maxWidth * clamped, cropped = clamped != ratio)
    }
}
