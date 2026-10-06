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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
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

    /** The open photo, for the transcript to draw its share of near the bubble. */
    internal var landing by mutableStateOf<ChatPhotoLanding?>(null)

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
 * The photo the overlay is flying: what it draws, and where, in the overlay's coordinates at
 * [origin] in the root.
 */
internal class ChatPhotoLanding(
    val painter: Painter,
    val frame: () -> ChatMediaFrame,
    val origin: () -> Offset,
)

/**
 * Reports the part of this chat its bars do not cover to [LocalChatPhotoSources]: these bounds
 * less [top] and [bottom]. Also draws the open photo's [ChatMediaFrame.handoff] share over the
 * transcript, so apply it inside any blur or haze the bars put on the transcript: the photo then
 * passes under them the way its bubble does.
 */
@Composable
fun Modifier.chatPhotoTranscript(sources: ChatPhotoSources, top: Dp, bottom: Dp): Modifier {
    val density = LocalDensity.current
    var origin = Offset.Zero
    return this
        .onGloballyPositioned {
            val bounds = it.boundsInRoot()
            origin = bounds.topLeft
            with(density) {
                sources.transcript = Rect(bounds.left, bounds.top + top.toPx(), bounds.right, bounds.bottom - bottom.toPx())
            }
        }
        .drawWithContent {
            drawContent()
            val landing = sources.landing ?: return@drawWithContent
            val frame = landing.frame()
            if (frame.handoff <= 0f) return@drawWithContent
            translate(landing.origin().x - origin.x, landing.origin().y - origin.y) {
                drawChatPhoto(landing.painter, frame.rect, frame.corners, frame.contentAlpha * frame.handoff)
            }
        }
}

/**
 * [painter] cropped to fill [rect] with [corners], as the overlay's `Image` draws it with
 * [ContentScale.Crop], so the two copies line up while one fades into the other.
 */
internal fun DrawScope.drawChatPhoto(painter: Painter, rect: Rect, corners: ChatPhotoCorners, alpha: Float) {
    if (rect.width <= 0f || rect.height <= 0f || alpha <= 0f) return
    val ltr = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = rect,
                topLeft = CornerRadius(if (ltr) corners.topStart else corners.topEnd),
                topRight = CornerRadius(if (ltr) corners.topEnd else corners.topStart),
                bottomRight = CornerRadius(if (ltr) corners.bottomEnd else corners.bottomStart),
                bottomLeft = CornerRadius(if (ltr) corners.bottomStart else corners.bottomEnd),
            ),
        )
    }
    val src = painter.intrinsicSize
    val drawn = if (src.isUnspecified || src.width <= 0f || src.height <= 0f) {
        rect.size
    } else {
        val scale = ContentScale.Crop.computeScaleFactor(src, rect.size)
        Size(src.width * scale.scaleX, src.height * scale.scaleY)
    }
    clipPath(path) {
        translate(rect.left + (rect.width - drawn.width) / 2f, rect.top + (rect.height - drawn.height) / 2f) {
            with(painter) { draw(drawn, alpha) }
        }
    }
}
