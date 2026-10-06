package com.flipcash.shared.chat.ui.media

import com.getcode.theme.CodeTheme
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.core.content.FileProvider
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.flipcash.core.R
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

internal const val VIEWER_IMAGE_TAG = "chat_media_viewer_image"
internal const val VIEWER_CLOSE_TAG = "chat_media_viewer_close"
internal const val VIEWER_SHARE_TAG = "chat_media_viewer_share"

private const val DISMISS_DISTANCE_FRACTION = 0.2f
private const val MIN_BACKGROUND_ALPHA = 0.2f

/**
 * A full-screen photo.
 *
 * Pinch zooms from 1× to 4× with the pan held inside the image's edges; a double tap goes to 2.5×
 * about the tapped point, or back to fit when already zoomed. A one-finger drag that starts while
 * the image is at fit pulls it away; when zoomed the same drag pans instead.
 *
 * On its own the pull moves the image down, fading the black behind it, and past a fifth of the
 * screen's height calls [onDismiss]. A host that draws the photo over something else, such as
 * [ChatPhotoOverlay], takes the whole presentation instead: [frame] says where the photo sits and
 * how opaque everything is, [onPull] and [onPullEnd] receive the drag, and [onDismiss] stays the
 * close button's.
 *
 * [placeholder] (usually the bubble's BlurHash, or the thumbnail already on screen) shows until
 * the full image has loaded, which [onFullImageLoaded] reports. Share stays disabled until
 * [fullImageLoaded] is true, since there is nothing worth sharing before then.
 *
 * Hostable as a Nav3 entry: it takes plain parameters and calls back for close and share. The host
 * owns what Share does; [shareImage] is the usual answer.
 *
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
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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
                .testTag(VIEWER_IMAGE_TAG)
                .pointerInput(zoomState, reduceMotion) {
                    detectTapGestures(
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

        val chrome = Modifier.graphicsLayer { alpha = frame?.invoke()?.chromeAlpha ?: 1f }
        ViewerButton(
            icon = Icons.Filled.Close,
            description = stringResource(R.string.description_chatMediaClose),
            enabled = interactive,
            tag = VIEWER_CLOSE_TAG,
            onClick = onDismiss,
            modifier = chrome.align(Alignment.TopStart),
        )
        ViewerButton(
            icon = Icons.Outlined.IosShare,
            description = stringResource(R.string.description_chatMediaShare),
            enabled = fullImageLoaded && interactive,
            tag = VIEWER_SHARE_TAG,
            onClick = onShare,
            modifier = chrome.align(Alignment.TopEnd),
        )
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
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .statusBarsPadding()
            .padding(CodeTheme.dimens.staticGrid.x2)
            .size(CodeTheme.dimens.staticGrid.x8)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
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
 * The directory the share sheet may read chat photos from. `res/xml/file_paths.xml` in the app
 * exposes `cacheDir/chat_media/` through the existing `${applicationId}.fileprovider` authority;
 * a file anywhere else makes [FileProvider] throw.
 */
fun chatMediaShareDir(context: Context): File =
    File(context.cacheDir, "chat_media").apply { if (!exists()) mkdirs() }

/**
 * Opens the system share sheet for [file], a JPEG under [chatMediaShareDir]. Returns false, rather
 * than throwing, when the file is outside the provider's paths or nothing can handle the intent.
 */
fun shareImage(context: Context, file: File, mimeType: String = "image/jpeg"): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        type = mimeType
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.title_chatMediaShare))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}.isSuccess
