package com.flipcash.shared.chat.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.flipcash.shared.chat.media.ChatPhoto
import androidx.compose.ui.unit.Dp
import com.flipcash.shared.chat.ui.BubblePosition
import com.flipcash.shared.chat.ui.bubbleCorners

/** What the overlay needs of one on-screen photo bubble: where it is, and what it draws. */
@Stable
class ChatPhotoSource internal constructor(
    internal val model: ChatPhoto,
    /** The photo's own size, for the shape it opens to. Null when the message did not say. */
    internal val imageSize: Size?,
    internal val corners: ChatPhotoCorners,
) {
    internal var coordinates: LayoutCoordinates? = null

    /** The bubble's bounds in the root's coordinates, or null when it is not on screen. */
    internal fun boundsInRoot(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInRoot()
}

/**
 * The photo bubbles currently composed in the transcript, by message id. The overlay reads a
 * bubble's bounds when it opens and again when it closes, since the row may have moved in between,
 * and hides the bubble ([hiddenId]) while the photo is drawn over it.
 */
@Stable
class ChatPhotoSources {
    private val sources = HashMap<Long, ChatPhotoSource>()

    /**
     * The part of the chat its top and bottom bars do not cover, in the root's coordinates. A
     * bubble under a bar is drawn behind it, so the photo landing there is clipped to this.
     */
    internal var transcript: Rect? = null

    /** The message whose bubble is covered by the overlay and drawn at zero alpha. */
    var hiddenId by mutableStateOf<Long?>(null)

    internal fun register(id: Long, source: ChatPhotoSource) {
        sources[id] = source
    }

    internal fun unregister(id: Long, source: ChatPhotoSource) {
        if (sources[id] === source) sources.remove(id)
    }

    internal fun sourceOf(id: Long): ChatPhotoSource? = sources[id]
}

val LocalChatPhotoSources = staticCompositionLocalOf<ChatPhotoSources?> { null }

/**
 * Registers this photo box with the [LocalChatPhotoSources] under [messageId] while it is
 * composed, and draws it at zero alpha while the overlay covers it. Does nothing without a [model]
 * or a registry.
 */
@Composable
internal fun Modifier.chatPhotoSource(
    messageId: Long?,
    model: ChatPhoto?,
    imageWidth: Int?,
    imageHeight: Int?,
    corners: ChatPhotoCorners,
): Modifier {
    val sources = LocalChatPhotoSources.current
    if (sources == null || messageId == null || model == null) return this
    val source = remember(model, imageWidth, imageHeight, corners) {
        val size = if ((imageWidth ?: 0) > 0 && (imageHeight ?: 0) > 0) {
            Size(imageWidth!!.toFloat(), imageHeight!!.toFloat())
        } else {
            null
        }
        ChatPhotoSource(model, size, corners)
    }
    DisposableEffect(sources, messageId, source) {
        sources.register(messageId, source)
        onDispose { sources.unregister(messageId, source) }
    }
    return this
        .onGloballyPositioned { source.coordinates = it }
        .graphicsLayer { alpha = if (sources.hiddenId == messageId) 0f else 1f }
}

/** The corners a photo bubble at [position] settles on, in px. */
@Composable
internal fun bubblePhotoCorners(position: BubblePosition, isFromSelf: Boolean): ChatPhotoCorners {
    val c = bubbleCorners(position, isFromSelf)
    return with(LocalDensity.current) {
        ChatPhotoCorners(c.topStart.toPx(), c.topEnd.toPx(), c.bottomEnd.toPx(), c.bottomStart.toPx())
    }
}

/**
 * Reports the part of this chat its bars do not cover to [LocalChatPhotoSources]: these bounds
 * less [top] and [bottom].
 */
@Composable
fun Modifier.chatPhotoTranscript(sources: ChatPhotoSources, top: Dp, bottom: Dp): Modifier {
    val density = LocalDensity.current
    return onGloballyPositioned {
        val bounds = it.boundsInRoot()
        with(density) {
            sources.transcript = Rect(bounds.left, bounds.top + top.toPx(), bounds.right, bounds.bottom - bottom.toPx())
        }
    }
}
