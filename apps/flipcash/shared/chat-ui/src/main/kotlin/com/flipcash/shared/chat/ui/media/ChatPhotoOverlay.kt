package com.flipcash.shared.chat.ui.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

// The attach card's spring (iOS `attachCard`, 0.30s, no bounce): iOS's zoom transition does not
// overshoot either, and a photo that does would carry past the screen.
private const val DAMPING = 1f
private const val STIFFNESS = 439f

/**
 * The open, drag and close of the photo overlay, as one progress and one pull.
 *
 * [progress] is 0 with the photo on its bubble and 1 with it open. The frame lerps between the
 * bubble's rect, looked up live so it can move, and an anchor: the fitted rect, shrunk and moved by
 * the pull while the finger is down. Closing freezes the anchor where the finger left it and runs
 * [progress] back to 0, so it is one spring in either direction and is never retargeted per frame.
 */
@Stable
internal class ChatPhotoPresentation(
    private val scope: CoroutineScope,
    private val lookup: () -> Rect?,
    private val sourceCorners: ChatPhotoCorners,
    private val transcript: () -> Rect?,
    private val content: () -> Size,
    private val density: Float,
    private val reduceMotion: Boolean,
    private val onClosed: () -> Unit,
) {
    /** The overlay's size, and where its origin sits in the root the bubbles report in. */
    var container by mutableStateOf(Size.Zero)
    var origin: Offset = Offset.Zero

    var closing by mutableStateOf(false)
        private set

    private val progress = Animatable(0f)
    private val pull = Animatable(Offset.Zero, Offset.VectorConverter)
    private var frozen: Frozen? by mutableStateOf(null)
    private var hasSource = true
    private var lastSource: Rect? = null

    private class Frozen(val rect: Rect, val pull: Float)

    init {
        hasSource = readSource() != null
    }

    private fun readSource(): Rect? {
        val bounds = lookup()?.translate(-origin) ?: return null
        val usable = container == Size.Zero || ChatPhotoTransition.sourceUsable(bounds, container)
        return if (usable) bounds.also { lastSource = it } else null
    }

    fun frame(): ChatMediaFrame {
        val size = container
        if (size == Size.Zero) return ChatMediaFrame.Hidden
        val source = if (hasSource) readSource() ?: lastSource else null
        val open = ChatPhotoTransition.fittedRect(size, content())
        val held = frozen
        val anchor: Rect
        val t: Float
        if (held != null) {
            anchor = held.rect
            t = held.pull
        } else {
            val offset = pull.value
            t = ChatPhotoTransition.dragProgress(offset, size.height)
            anchor = ChatPhotoTransition.draggedRect(open, offset, t)
        }
        return ChatPhotoTransition.frame(
            source = source,
            sourceCorners = sourceCorners,
            anchor = anchor,
            anchorPull = t,
            progress = progress.value,
            transcript = transcript()?.translate(-origin),
            container = size,
        )
    }

    suspend fun open() {
        if (reduceMotion) progress.snapTo(1f) else progress.animateTo(1f, spring(DAMPING, STIFFNESS, 0.001f))
    }

    fun onPull(delta: Offset) {
        if (closing) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) { pull.snapTo(pull.value + delta) }
    }

    /** Called when the finger lifts after a pull, with its [velocity] in px per second. */
    fun onPullEnd(velocity: Offset) {
        if (closing) return
        if (ChatPhotoTransition.shouldDismiss(pull.value, velocity, container.height, density)) {
            dismiss()
        } else {
            scope.launch {
                if (reduceMotion) pull.snapTo(Offset.Zero) else pull.animateTo(Offset.Zero, spring(DAMPING, STIFFNESS))
            }
        }
    }

    /** Animates the photo back into its bubble, looked up again now, then reports [onClosed]. */
    fun dismiss() {
        if (closing) return
        val size = container
        val open = ChatPhotoTransition.fittedRect(size, content())
        val offset = pull.value
        val t = ChatPhotoTransition.dragProgress(offset, size.height)
        frozen = Frozen(ChatPhotoTransition.draggedRect(open, offset, t), t)
        hasSource = readSource() != null
        closing = true
        scope.launch {
            if (reduceMotion) progress.snapTo(0f) else progress.animateTo(0f, spring(DAMPING, STIFFNESS, 0.001f))
            onClosed()
        }
    }
}

/**
 * The photo of message [messageId], opened over the chat: it grows out of its bubble in [sources],
 * drags back into it, and goes when it lands. Compose it above the whole screen with the chat
 * still live underneath, only while a photo is open.
 *
 * The caller hides the keyboard first, so the bubble the photo grows from is where it will rest.
 */
@Composable
fun ChatPhotoOverlay(
    messageId: Long,
    sources: ChatPhotoSources,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReducedMotion(),
) {
    val source = remember(messageId) { sources.sourceOf(messageId) }
    if (source == null) {
        LaunchedEffect(messageId) { onClosed() }
        return
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val currentOnClosed by rememberUpdatedState(onClosed)
    val zoomState = remember(messageId) { ChatMediaZoomState() }
    val windowSize = LocalWindowInfo.current.containerSize

    val presentation = remember(messageId) {
        ChatPhotoPresentation(
            scope = scope,
            lookup = { source.boundsInRoot() },
            sourceCorners = source.corners,
            transcript = { sources.transcript },
            content = {
                source.imageSize
                    ?: zoomState.content.takeIf { it.width > 0f && it.height > 0f }
                    ?: Size.Zero
            },
            density = density,
            reduceMotion = reduceMotion,
            onClosed = { currentOnClosed() },
        ).also { it.container = Size(windowSize.width.toFloat(), windowSize.height.toFloat()) }
    }

    // Hidden in the composition that adds the overlay and shown in the one that removes it, so the
    // bubble and the photo are never both drawn, and never both missing.
    DisposableEffect(sources, messageId) {
        sources.hiddenId = messageId
        onDispose { if (sources.hiddenId == messageId) sources.hiddenId = null }
    }
    LaunchedEffect(presentation) { presentation.open() }
    BackHandler { presentation.dismiss() }

    // The bubble already has the photo cached at its own size: draw that until the full one lands.
    val request = remember(source) {
        ImageRequest.Builder(context)
            .data(source.model)
            .placeholderMemoryCacheKey(source.model.cacheKey)
            .build()
    }
    var fullImageLoaded by remember(messageId) { mutableStateOf(false) }
    val share = rememberSharePhoto(source.model)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { presentation.container = Size(it.width.toFloat(), it.height.toFloat()) }
            .onGloballyPositioned { presentation.origin = it.positionInRoot() },
    ) {
        ChatMediaViewer(
            model = request,
            fullImageLoaded = fullImageLoaded,
            onFullImageLoaded = { fullImageLoaded = true },
            onShare = share,
            onDismiss = presentation::dismiss,
            zoomState = zoomState,
            reduceMotion = reduceMotion,
            frame = presentation::frame,
            onPull = presentation::onPull,
            onPullEnd = presentation::onPullEnd,
            interactive = !presentation.closing,
        )
    }
}
