package com.flipcash.shared.chat.ui.media

import com.getcode.theme.CodeTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import com.flipcash.shared.chat.reactions.ReactionStrip
import com.flipcash.shared.chat.ui.QuickReactionStrip
import com.getcode.ui.components.glass.floatingGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.flipcash.core.R
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val VIEWER_IMAGE_TAG = "chat_media_viewer_image"
internal const val VIEWER_CLOSE_TAG = "chat_media_viewer_close"
internal const val VIEWER_REACT_TAG = "chat_media_viewer_react"
internal const val VIEWER_REACT_GLYPH_TAG = "chat_media_viewer_react_glyph"
internal const val VIEWER_REACT_MORE_TAG = "chat_media_viewer_react_more"
internal const val VIEWER_REACT_STRIP_TAG = "chat_media_viewer_react_strip"
internal const val VIEWER_REACT_SCRIM_TAG = "chat_media_viewer_react_scrim"
internal const val VIEWER_REPLY_TAG = "chat_media_viewer_reply"
internal const val VIEWER_SAVE_TAG = "chat_media_viewer_save"
internal const val VIEWER_FORWARD_TAG = "chat_media_viewer_forward"

private const val DISMISS_DISTANCE_FRACTION = 0.2f
private const val MIN_BACKGROUND_ALPHA = 0.2f

/**
 * A full-screen photo.
 *
 * Pinch zooms from 1× to 4× with the pan held inside the image's edges; a double tap goes to 2.5×
 * about the tapped point, or back to fit when already zoomed. The viewer opens with no chrome; a
 * single tap shows or hides the close button and the two pills at the bottom (React and Reply on the
 * left, Save and Forward on the right). A one-finger drag that starts while
 * the image is at fit pulls it away; when zoomed the same drag pans instead.
 *
 * On its own the pull moves the image down, fading the black behind it, and past a fifth of the
 * screen's height calls [onDismiss]. A host that draws the photo over something else, such as
 * [ChatPhotoOverlay], takes the whole presentation instead: [frame] says where the photo sits and
 * how opaque everything is, [onPull] and [onPullEnd] receive the drag, and [onDismiss] stays the
 * close button's.
 *
 * [placeholder] (usually the bubble's BlurHash, or the thumbnail already on screen) shows until
 * the full image has loaded, which [onFullImageLoaded] reports. Save stays disabled until
 * [fullImageLoaded] is true, since there is nothing worth saving before then.
 *
 * Hostable as a Nav3 entry: it takes plain parameters and calls back for close, save, reply and
 * forward. The host owns what each does; [rememberSavePhoto] is the usual answer to Save.
 *
 * @param ownReactions the emoji the viewer has reacted with, in the order the pill row shows them;
 * the React button draws them in place of its icon.
 * @param onReact a pick from [reactionStrip]; null hides the React button, for a message that
 * cannot be reacted to. The button opens the strip above it, and a pick, a tap outside it or
 * hiding the chrome closes it again.
 * @param onOpenPicker the strip's "+": the host's full emoji picker.
 * @param onReply Reply; null hides the button, for a message that cannot be replied to.
 * @param onForward Forward; null hides the button, for a message with no link to hand out.
 * @param model what Coil loads for the full image.
 * @param frame the photo's rect, corners and the opacities, read at layout and draw time so a
 * host can animate them without recomposing. Null fills the container.
 * @param interactive false while the host is closing the viewer: touches and buttons do nothing.
 * @param onPainter receives the painter the photo is drawn with, for a host that draws a copy of it.
 */
@Composable
fun ChatMediaViewer(
    model: Any?,
    fullImageLoaded: Boolean,
    onFullImageLoaded: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onReact: ((emoji: String) -> Unit)? = null,
    reactionStrip: List<ReactionStrip.Entry> = emptyList(),
    ownReactions: List<String> = emptyList(),
    onOpenPicker: () -> Unit = {},
    onReply: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    placeholder: Painter? = null,
    zoomState: ChatMediaZoomState = remember { ChatMediaZoomState() },
    reduceMotion: Boolean = rememberReducedMotion(),
    frame: (() -> ChatMediaFrame)? = null,
    onPull: ((Offset) -> Unit)? = null,
    onPullEnd: ((Offset) -> Unit)? = null,
    interactive: Boolean = true,
    onPainter: ((Painter) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnLoaded by rememberUpdatedState(onFullImageLoaded)
    val currentInteractive by rememberUpdatedState(interactive)
    val currentOnPull by rememberUpdatedState(onPull)
    val currentOnPullEnd by rememberUpdatedState(onPullEnd)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var chromeVisible by remember { mutableStateOf(false) }
    var stripOpen by remember { mutableStateOf(false) }
    LaunchedEffect(chromeVisible) { if (!chromeVisible) stripOpen = false }
    val chromeShown by animateFloatAsState(
        targetValue = if (chromeVisible) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(200),
        label = "chrome",
    )

    val painter = rememberAsyncImagePainter(
        model = model,
        onState = { state ->
            if (state is AsyncImagePainter.State.Success) {
                zoomState.content = state.painter.intrinsicSize
                currentOnLoaded()
            }
        },
    )
    if (onPainter != null) SideEffect { onPainter(painter) }

    val hazeState = rememberHazeState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val alpha = if (frame != null) {
                    frame().backdrop
                } else if (size.height > 0f) {
                    // Fade the black out as the image is pulled away, never to nothing: the
                    // transcript should show through, but the photo should still read against it.
                    (1f - abs(dragY.value) / (size.height * 0.5f)).coerceIn(MIN_BACKGROUND_ALPHA, 1f)
                } else {
                    1f
                }
                drawRect(Color.Black, alpha = alpha)
            }
            .onSizeChanged { zoomState.container = Size(it.width.toFloat(), it.height.toFloat()) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .testTag(VIEWER_IMAGE_TAG)
                .pointerInput(zoomState, reduceMotion) {
                    detectTapGestures(
                        onTap = { if (currentInteractive) chromeVisible = !chromeVisible },
                        onDoubleTap = { tap ->
                            if (currentInteractive) {
                                scope.launch { zoomState.toggle(tap, animated = !reduceMotion) }
                            }
                        },
                    )
                }
                .pointerInput(zoomState, touchSlop) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (!currentInteractive) {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        val tracker = VelocityTracker()
                        var pulling = false
                        var totalPan = Offset.Zero
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val multiTouch = event.changes.count { it.pressed } > 1
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            when {
                                multiTouch || zoomState.isZoomed -> {
                                    zoomState.transform(event.calculateCentroid(), pan, zoom)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }

                                pulling -> {
                                    event.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                                    pullBy(pan, currentOnPull) { delta -> scope.launch { dragY.snapTo(dragY.value + delta.y) } }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }

                                else -> {
                                    totalPan += pan
                                    if (abs(totalPan.y) > touchSlop && abs(totalPan.y) > abs(totalPan.x)) {
                                        pulling = true
                                        event.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                                        pullBy(totalPan, currentOnPull) { delta -> scope.launch { dragY.snapTo(delta.y) } }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // A pinch that ended below fit settles at fit.
                        if (zoomState.scale <= ChatMediaZoom.MIN_SCALE) zoomState.reset()
                        if (pulling) {
                            val end = currentOnPullEnd
                            if (end != null) {
                                val v = tracker.calculateVelocity()
                                end(Offset(v.x, v.y))
                            } else {
                                val past = abs(dragY.value) >
                                    zoomState.container.height * DISMISS_DISTANCE_FRACTION
                                if (past) {
                                    currentOnDismiss()
                                } else {
                                    scope.launch {
                                        if (reduceMotion) dragY.snapTo(0f) else dragY.animateTo(0f, tween(200))
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .drawWithContent {
                        // In the container's coordinates, before the photo is placed in it: keeps a
                        // photo landing on a bubble under the chat's bars behind them, as the bubble is.
                        val clip = frame?.invoke()?.clip
                        if (clip == null) {
                            drawContent()
                        } else {
                            clipRect(clip.left, clip.top, clip.right, clip.bottom) { this@drawWithContent.drawContent() }
                        }
                    }
                    .layout { measurable, constraints ->
                        // The photo's own frame inside the container, which a host animates; the
                        // whole container when it does not.
                        val rect = frame?.invoke()?.rect
                        val w = rect?.width?.roundToInt() ?: constraints.maxWidth
                        val h = rect?.height?.roundToInt() ?: constraints.maxHeight
                        val placeable = measurable.measure(
                            Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(rect?.left?.roundToInt() ?: 0, rect?.top?.roundToInt() ?: 0)
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoomState.scale
                        scaleY = zoomState.scale
                        translationX = zoomState.offset.x
                        translationY = zoomState.offset.y + if (frame == null) dragY.value else 0f
                        if (frame != null) {
                            val f = frame()
                            // The transcript draws the rest, under the chat's bars.
                            alpha = f.contentAlpha * (1f - f.handoff)
                            val c = f.corners
                            shape = RoundedCornerShape(c.topStart, c.topEnd, c.bottomEnd, c.bottomStart)
                            clip = !c.isSquare
                        }
                    },
            ) {
                // Filled while a host frames the photo (its aspect eases to the photo's own by the
                // time it is open); fitted when the viewer owns the whole screen.
                val scale = if (frame != null) ContentScale.Crop else ContentScale.Fit
                // The placeholder sits under the real image, which stays composed so the load
                // runs, and which draws nothing until it has something.
                if (!fullImageLoaded && placeholder != null) {
                    Image(
                        painter = placeholder,
                        contentDescription = null,
                        contentScale = scale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Image(
                    painter = painter,
                    contentDescription = stringResource(R.string.label_chat_media_photo),
                    contentScale = scale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val chrome = Modifier.graphicsLayer { alpha = (frame?.invoke()?.chromeAlpha ?: 1f) * chromeShown }
        ViewerButton(
            icon = Icons.Filled.Close,
            description = stringResource(R.string.description_chatMediaClose),
            enabled = interactive,
            visible = chromeVisible,
            tag = VIEWER_CLOSE_TAG,
            hazeState = hazeState,
            onClick = onDismiss,
            modifier = chrome.align(Alignment.TopStart),
        )
        if (stripOpen && onReact != null && reactionStrip.isNotEmpty()) {
            // A tap anywhere else closes the strip without reacting.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(VIEWER_REACT_SCRIM_TAG)
                    .pointerInput(Unit) { detectTapGestures { stripOpen = false } },
            )
            QuickReactionStrip(
                entries = reactionStrip,
                onToggle = { emoji ->
                    stripOpen = false
                    onReact(emoji)
                },
                onOpenPicker = {
                    stripOpen = false
                    onOpenPicker()
                },
                glass = hazeState,
                modifier = chrome
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = CodeTheme.dimens.staticGrid.x4,
                        bottom = CodeTheme.dimens.staticGrid.x3 + CodeTheme.dimens.staticGrid.x10 +
                            CodeTheme.dimens.staticGrid.x2,
                    )
                    .testTag(VIEWER_REACT_STRIP_TAG),
            )
        }
        Row(
            modifier = chrome
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = CodeTheme.dimens.staticGrid.x4, vertical = CodeTheme.dimens.staticGrid.x3),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ViewerPill(hazeState) {
                if (onReact != null) {
                    ViewerPillButton(
                        icon = Icons.Outlined.AddReaction,
                        emoji = ownReactions,
                        description = reactDescription(ownReactions),
                        // Nothing to show until the strip's inputs have loaded.
                        enabled = interactive && chromeVisible && reactionStrip.isNotEmpty(),
                        tag = VIEWER_REACT_TAG,
                        onClick = { stripOpen = !stripOpen },
                    )
                }
                if (onReply != null) {
                    ViewerPillButton(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        description = stringResource(R.string.action_reply),
                        enabled = interactive && chromeVisible,
                        tag = VIEWER_REPLY_TAG,
                        onClick = onReply,
                    )
                }
            }
            ViewerPill(hazeState) {
                ViewerPillButton(
                    icon = Icons.Outlined.Download,
                    description = stringResource(R.string.description_chatMediaSave),
                    enabled = fullImageLoaded && interactive && chromeVisible,
                    tag = VIEWER_SAVE_TAG,
                    onClick = onSave,
                )
                if (onForward != null) {
                    ViewerPillButton(
                        icon = ForwardArrow,
                        description = stringResource(R.string.action_forwardMessage),
                        enabled = interactive && chromeVisible,
                        tag = VIEWER_FORWARD_TAG,
                        onClick = onForward,
                    )
                }
            }
        }
    }

    LaunchedEffect(model) { zoomState.reset() }
}

/** Sends a pull to the host's [external] handler when it has one, else to the viewer's own [own]. */
private fun pullBy(delta: Offset, external: ((Offset) -> Unit)?, own: (Offset) -> Unit) {
    if (external != null) external(delta) else own(delta)
}

@Composable
private fun ViewerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    visible: Boolean,
    tag: String,
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        // Hidden buttons fade out at full tint but stop taking taps straight away.
        enabled = enabled && visible,
        modifier = modifier
            .statusBarsPadding()
            .padding(CodeTheme.dimens.staticGrid.x2)
            .size(CodeTheme.dimens.staticGrid.x8)
            .floatingGlass(hazeState)
            .testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** The capsule behind a group of bottom buttons: frosted glass over the photo, as the viewer's other chrome. */
@Composable
private fun ViewerPill(hazeState: HazeState, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .floatingGlass(hazeState),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ViewerPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    emoji: List<String> = emptyList(),
) {
    if (emoji.isNotEmpty()) {
        // Not an IconButton: that pins its content to a 40dp square, which would clip the stack.
        Box(
            modifier = Modifier
                .height(CodeTheme.dimens.staticGrid.x10)
                .widthIn(min = CodeTheme.dimens.staticGrid.x10)
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(tag),
            contentAlignment = Alignment.Center,
        ) { ReactionGlyphs(emoji, dimmed = !enabled) }
        return
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(CodeTheme.dimens.staticGrid.x10)
            .semantics { contentDescription = description }
            .testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/**
 * The user's own reactions as a stack of chips: each emoji sits in its own circle, ringed in the
 * glass tint's opaque equivalent so each chip is cut out of the one beneath it. Up to three show;
 * more show the first two and a "+N" chip. Later chips sit on top of earlier ones.
 */
@Composable
private fun ReactionGlyphs(emoji: List<String>, dimmed: Boolean) {
    val shown = if (emoji.size > 3) emoji.take(2) else emoji
    val count = shown.size + if (emoji.size > 3) 1 else 0
    val backdrop = CodeTheme.colors.background
    val ringTint = lerp(backdrop, Color.White, 0.18f)
    val chipTint = lerp(backdrop, Color.White, 0.32f)
    val chipSize = 28.dp
    // Each chip starts 65% of its width after the last, so neighbours overlap by 35%.
    val stride = chipSize * 0.65f
    Box(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.staticGrid.x2)
            .width(chipSize + stride * (count - 1))
            .height(chipSize)
            .graphicsLayer { alpha = if (dimmed) 0.4f else 1f },
    ) {
        shown.forEachIndexed { index, glyph ->
            ReactionChip(chipSize, chipTint, ringTint, Modifier.offset(x = stride * index)) {
                Text(text = glyph, fontSize = 14.sp, modifier = Modifier.testTag(VIEWER_REACT_GLYPH_TAG))
            }
        }
        if (emoji.size > 3) {
            ReactionChip(chipSize, chipTint, ringTint, Modifier.offset(x = stride * shown.size)) {
                Text(
                    text = "+${emoji.size - 2}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag(VIEWER_REACT_MORE_TAG),
                )
            }
        }
    }
}

@Composable
private fun ReactionChip(
    size: androidx.compose.ui.unit.Dp,
    fill: Color,
    ring: Color,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun reactDescription(own: List<String>): String {
    val base = stringResource(R.string.action_addReaction)
    return if (own.isEmpty()) base else stringResource(R.string.description_chatMediaReactWith, base, own.joinToString(" "))
}
