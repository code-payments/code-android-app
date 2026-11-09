package com.flipcash.app.messenger.internal.request

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import com.flipcash.shared.chat.ui.request.RequestMotion
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import com.flipcash.shared.chat.ui.ActionCapsule
import com.flipcash.shared.chat.ui.ActionCapsuleButton
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.flipcash.services.models.paymentrequest.StatusLine
import com.flipcash.services.models.paymentrequest.unpaidShareOf
import com.flipcash.services.models.paymentrequest.ViewerStanding
import com.flipcash.services.models.paymentrequest.viewerStanding
import com.flipcash.shared.chat.ui.request.PaidBadge
import com.flipcash.shared.chat.ui.request.ViewerStatusLine
import com.flipcash.shared.chat.ui.request.description
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.statusLine
import com.flipcash.shared.chat.ui.request.PaymentRequestCardContent
import com.flipcash.shared.chat.ui.request.requestText
import com.flipcash.shared.chat.ui.request.text
import com.getcode.opencode.model.core.ID
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * The shared-element scope the deck's stack (under the app bar) and the expanded list (over the
 * transcript) share, so the front card can move between them. Null outside [RequestDeckLayout].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalDeckTransition = compositionLocalOf<SharedTransitionScope?> { null }

/** Hosts the screen so the deck's front card can move between the stack and the expanded list. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RequestDeckLayout(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalDeckTransition provides this) {
            Box(content = content)
        }
    }
}

/** A deck request's title, or null for one the deck cannot draw (unsupported or not opened). */
internal val PaymentRequest.deckTitle: String?
    get() = (split?.text as? PaymentRequestText.Plain)?.title

private fun sharedKey(id: ID, part: String) = "payment-request-${id.hashCode()}-$part"

/**
 * Ties one piece of the front card (the container, the title, the ring or the amounts) to its
 * counterpart in the other state, so each moves on its own rather than fading with the card.
 * Text scales to its new bounds, since the stack and the expanded card set it in different styles.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.deckShared(
    id: ID,
    part: String,
    visibility: AnimatedVisibilityScope?,
    shape: androidx.compose.ui.graphics.Shape? = null,
): Modifier {
    val transition = LocalDeckTransition.current ?: return this
    if (visibility == null) return this
    return with(transition) {
        val state = rememberSharedContentState(sharedKey(id, part))
        if (shape != null) {
            this@deckShared.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibility,
                resizeMode = scaleToBounds(),
                clipInOverlayDuringTransition = OverlayClip(shape),
            )
        } else {
            this@deckShared.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibility,
                resizeMode = scaleToBounds(),
            )
        }
    }
}

/** Where the deck is: resting as the stack under the app bar, or expanded over the transcript. */
internal enum class DeckPlacement {
    /** The full-width card stack under the app bar, for every viewer (concept node 12670:1262). */
    Strip,

    /** Every request as a card over the transcript. */
    Expanded,
}

/**
 * The deck as a full-width card stack under the app bar (concept node 12670:1262), for every
 * viewer. It stays put while the timeline scrolls, and keeps its room while expanded so the
 * timeline doesn't move under the expanded cards. Arriving or leaving after the chat is on screen
 * it fades and scales in place while the room under it opens or closes; a deck there when the chat
 * opens is in place on the first frame.
 */
@Composable
internal fun RequestDeckStrip(
    deck: List<PaymentRequest>,
    now: Instant,
    viewerId: ID?,
    transition: Transition<DeckPlacement>,
    onExpand: () -> Unit,
    onCycle: () -> Unit,
    onPay: (PaymentRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(deck) }
    if (deck.isNotEmpty()) shown = deck
    AnimatedVisibility(
        visible = deck.isNotEmpty(),
        enter = expandVertically(tween(APPEAR_MILLIS), expandFrom = Alignment.Top) +
            fadeIn(tween(APPEAR_MILLIS)) +
            scaleIn(tween(APPEAR_MILLIS), initialScale = APPEAR_SCALE),
        exit = shrinkVertically(tween(APPEAR_MILLIS), shrinkTowards = Alignment.Top) +
            fadeOut(tween(APPEAR_MILLIS)) +
            scaleOut(tween(APPEAR_MILLIS), targetScale = APPEAR_SCALE),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .testTag(DECK_TAG)
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x2)
                .height(STRIP_HEIGHT + STRIP_STEP * 2),
        ) {
            DeckStack(
                deck = shown,
                now = now,
                viewerId = viewerId,
                transition = transition,
                onExpand = onExpand,
                onCycle = onCycle,
                onPay = onPay,
            )
        }
    }
}

/**
 * Where the front card goes for one step of a vertical drag on the stack, from [value] by [delta],
 * with [heightPx] the card's height. Down follows the finger, rubber-banding past one card height;
 * with one request, or upward, it's a small elastic pull and nothing more.
 */
internal fun deckDragTarget(value: Float, delta: Float, canCycle: Boolean, heightPx: Float): Float {
    val pull = heightPx * ELASTIC_PULL
    return when {
        // Upward, or anywhere with one request: a small elastic pull.
        !canCycle -> (value + delta * ELASTIC_RESISTANCE).coerceIn(-pull, pull)
        value < 0f || (value == 0f && delta < 0f) ->
            (value + delta * ELASTIC_RESISTANCE).coerceIn(-pull, 0f)
        // Past one card height the card rubber-bands instead of sliding on into the chat.
        delta > 0f && value >= heightPx -> {
            val over = (value - heightPx) / (heightPx * RUBBER_BAND)
            value + delta * 0.5f * (1f - over).coerceAtLeast(0f)
        }
        else -> (value + delta).coerceAtLeast(0f)
    }
}

/** Whether a drag let go at [value] (moving at [velocity], px/s) moves the front card to the back. */
internal fun deckDragCycles(value: Float, velocity: Float, canCycle: Boolean, heightPx: Float): Boolean =
    canCycle && value > 0f && (value > heightPx * CYCLE_THRESHOLD || velocity > FLING_VELOCITY)

/**
 * The front request with up to two cards peeking beneath it. Swipe down cycles; tap expands; a
 * drag up is only an elastic pull. Shown while [transition] rests, whose scope its front card
 * moves to the expanded list in.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DeckStack(
    deck: List<PaymentRequest>,
    now: Instant,
    viewerId: ID?,
    transition: Transition<DeckPlacement>,
    onExpand: () -> Unit,
    onCycle: () -> Unit,
    onPay: (PaymentRequest) -> Unit,
) {
    transition.AnimatedVisibility(
        visible = { it == DeckPlacement.Strip },
        enter = fadeIn(tween(STRIP_MILLIS)),
        exit = fadeOut(tween(STRIP_MILLIS)),
    ) {
        val front = deck.firstOrNull() ?: return@AnimatedVisibility
        val shape = STRIP_SHAPE
        val transition = LocalDeckTransition.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val drag = remember { Animatable(0f) }
        val arrive = remember { Animatable(1f) }
        // Where the swipe was let go: how far the front card had travelled, and how far through
        // the swipe that was. The hand-over starts every card from there.
        var releaseDrag by remember { mutableFloatStateOf(0f) }
        var releaseProgress by remember { mutableFloatStateOf(1f) }
        // Set while the cards behind rise to close the gap a request that ended left, rather than
        // after a swipe, so no swiped card drops to the back.
        var closingGap by remember { mutableStateOf(false) }
        val cardHeightPx = with(density) { STRIP_HEIGHT.toPx() }
        val canCycle = deck.size > 1

        val ids = deck.map { it.id }
        var shownIds by remember { mutableStateOf(ids) }
        LaunchedEffect(ids) {
            val previous = shownIds
            shownIds = ids
            // The front request left the deck (it ended) and another took its slot: the cards
            // behind rise into place on the hand-over spring, from one slot back.
            if (previous.isNotEmpty() && previous.first() !in ids && ids.isNotEmpty()) {
                releaseDrag = 0f
                releaseProgress = 0f
                closingGap = true
                try {
                    drag.snapTo(0f)
                    arrive.snapTo(0f)
                    arrive.animateTo(1f, DECK_SPRING)
                } finally {
                    closingGap = false
                }
            }
        }

        val label = deckLabel(deck, now, viewerId)
        val nextLabel = stringResource(R.string.action_nextRequest)
        // Pay is a button of its own, so the deck's label goes on the rest of the front card.
        val deckSemantics = Modifier.clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            onClick { onExpand(); true }
            if (canCycle) customActions = listOf(CustomAccessibilityAction(nextLabel) { onCycle(); true })
        }
        Box(
            modifier = Modifier.padding(bottom = STRIP_STEP * 2),
        ) {
            // How far the front card is through being swiped to the back, 0 to 1. The cards behind
            // rise a slot with it, so the next request is already in place when the swipe lands.
            val progress = (drag.value / cardHeightPx).coerceIn(0f, 1f)
            val insetPx = with(density) { STRIP_INSET.toPx() }
            val stepPx = with(density) { STRIP_STEP.toPx() }
            // The card just swiped off, travelling from below the front slot to its slot at the back.
            val swiped = if (canCycle && !closingGap && arrive.value < 1f) deck.last() else null
            val swipedDepth = (deck.size - 1).coerceAtMost(2).toFloat()
            // How far each card still has to rise after a hand-over: the swipe was let go short of
            // a full card height, so the cards behind finish the step on the same spring.
            val handover = (1f - releaseProgress) * (1f - arrive.value)

            @Composable
            fun Card(request: PaymentRequest, layer: GraphicsLayerScope.() -> Unit, depth: Float, content: Boolean) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer(layer)
                        // The request rising from behind shows clipped to its card until the
                        // swipe is let go.
                        .clip(shape)
                        .background(CodeTheme.colors.background, shape)
                        .background(peekColor(depth), shape)
                        .border(1.dp, CodeTheme.colors.border, shape),
                ) {
                    if (content) {
                        StripFront(
                            request = request,
                            now = now,
                            viewerId = viewerId,
                            visibility = null,
                            onPay = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            if (swiped != null) {
                Card(
                    request = swiped,
                    depth = swipedDepth * arrive.value,
                    content = true,
                    layer = {
                        val t = arrive.value
                        val scale = lerp(1f - DROP_SCALE * releaseProgress, slotScale(swipedDepth, insetPx, size.width), t)
                        scaleX = scale
                        scaleY = scale
                        translationY = lerp(releaseDrag, slotY(swipedDepth, scale, stepPx, size.height), t)
                        // Past the second slot it tucks out of sight rather than stacking up.
                        if (deck.size > 3) alpha = 1f - t
                    },
                )
            }
            // The peeking cards, back first. Each is a scaled copy of the front slot, so the one
            // rising into it grows with its glyph, title and amounts as one piece.
            val peeks = deck.drop(1).take(3)
            peeks.withIndex().reversed().forEach { (index, request) ->
                if (request == swiped) return@forEach
                val depth = index + 1 - progress + handover
                // A third card behind only shows as the others move up.
                if (depth >= 3f) return@forEach
                Card(
                    request = request,
                    depth = depth,
                    // The card rising to the front shows its request as soon as the front moves.
                    content = index == 0 && drag.value > 0f,
                    layer = {
                        val scale = slotScale(depth, insetPx, size.width)
                        scaleX = scale
                        scaleY = scale
                        translationY = slotY(depth, scale, stepPx, size.height)
                        if (depth > 2f) alpha = 1f - (depth - 2f)
                    },
                )
            }
            StripFront(
                request = front,
                now = now,
                viewerId = viewerId,
                visibility = this@AnimatedVisibility,
                onPay = { onPay(front) },
                bodyModifier = deckSemantics,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (transition != null) with(transition) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(sharedKey(front.id, "card")),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                clipInOverlayDuringTransition = OverlayClip(shape),
                            )
                        } else Modifier
                    )
                    .graphicsLayer {
                        // Falls back as it goes, so it hands off at the size the back card starts from.
                        // Just after a hand-over it is still rising out of the slot behind.
                        val scale = (1f - DROP_SCALE * progress) * slotScale(handover, insetPx, size.width)
                        scaleX = scale
                        scaleY = scale
                        translationY = drag.value + slotY(handover, scale, stepPx, size.height)
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                drag.snapTo(deckDragTarget(drag.value, delta, canCycle, cardHeightPx))
                            }
                        },
                        onDragStopped = { velocity ->
                            if (deckDragCycles(drag.value, velocity, canCycle, cardHeightPx)) {
                                // Hand over on release: the card that rose becomes the front and the
                                // swiped one drops behind to the back, each from where it was.
                                releaseDrag = drag.value
                                releaseProgress = (drag.value / cardHeightPx).coerceIn(0f, 1f)
                                closingGap = false
                                drag.snapTo(0f)
                                arrive.snapTo(0f)
                                onCycle()
                                arrive.animateTo(1f, DECK_SPRING)
                            } else {
                                drag.animateTo(0f, DECK_SPRING, initialVelocity = velocity)
                            }
                        },
                    )
                    .clip(shape)
                    .clickable(onClick = onExpand),
            )
        }
    }
}

/** A peeking card's fill at [depth]: the front's surface one slot back, fading toward the next. */
@Composable
private fun peekColor(depth: Float): Color {
    val back = (depth - 1f).coerceIn(0f, 1f)
    return lerp(CodeTheme.colors.surfaceVariant, Color.White.copy(alpha = 0.08f), back)
}

/**
 * The stack's front card (concept node 12670:1262): glyph, title, "You owe <share> · <time left>",
 * Pay and a success outline while the viewer owes on it; otherwise the viewer's status line, no
 * Pay and no outline. Paying the last owed share changes it in place.
 */
@Composable
private fun StripFront(
    request: PaymentRequest,
    now: Instant,
    viewerId: ID?,
    visibility: AnimatedVisibilityScope?,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
) {
    val shape = STRIP_SHAPE
    val share = request.unpaidShareOf(viewerId, now)
    val standing = request.viewerStanding(viewerId, now)
    Row(
        modifier = modifier
            .height(STRIP_HEIGHT)
            .background(CodeTheme.colors.background, shape)
            .background(CodeTheme.colors.surfaceVariant, shape)
            .border(1.dp, if (share != null) CodeTheme.colors.success else CodeTheme.colors.border, shape)
            .padding(horizontal = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Row(
            modifier = Modifier.weight(1f).then(bodyModifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            RequestRing(
                request = request,
                now = now,
                size = STRIP_RING_SIZE,
                paid = standing is ViewerStanding.Paid,
                modifier = Modifier.deckShared(request.id, "glyph", visibility, CircleShape),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = request.deckTitle.orEmpty(),
                    style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.deckShared(request.id, "title", visibility),
                )
                val lineStyle = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.Medium)
                if (share != null) {
                    val owe = stringResource(R.string.label_requestYouOwe, share.amount.requestText())
                    val timeLeft = request.statusLine(now)?.timeLeftText().orEmpty()
                    val success = CodeTheme.colors.success
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = success)) { append(owe) }
                            if (timeLeft.isNotEmpty()) append(" · $timeLeft")
                        },
                        style = lineStyle,
                        color = CodeTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.deckShared(request.id, "amounts", visibility),
                    )
                } else {
                    ViewerStatusLine(
                        request = request,
                        standing = standing,
                        style = lineStyle,
                        color = CodeTheme.colors.textSecondary,
                        modifier = Modifier.deckShared(request.id, "amounts", visibility),
                    )
                }
            }
        }
        if (share != null) {
            val payLabel = stringResource(R.string.cd_requestPayShare, share.amount.requestText())
            Box(
                modifier = Modifier
                    .clip(PAY_SHAPE)
                    .background(CodeTheme.colors.success, PAY_SHAPE)
                    .clickable(onClick = onPay)
                    .clearAndSetSemantics {
                        contentDescription = payLabel
                        role = Role.Button
                        onClick { onPay(); true }
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_requestPay),
                    style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black,
                )
            }
        }
    }
}

/** The status line without "to pay" ("2 days left"); the ended lines as they are. */
@Composable
private fun StatusLine.timeLeftText(): String = when (this) {
    is StatusLine.DaysLeft -> pluralStringResource(R.plurals.label_requestDaysLeftShort, days, days)
    is StatusLine.HoursLeft -> pluralStringResource(R.plurals.label_requestHoursLeftShort, hours, hours)
    is StatusLine.MinutesLeft -> pluralStringResource(R.plurals.label_requestMinutesLeftShort, minutes, minutes)
    else -> text()
}

/**
 * The paid fraction as a ring around a "$" glyph, filled clockwise from 12 o'clock. A completed
 * request sweeps it to full while it stays in the deck for that, before it leaves. Grey once the
 * request has ended, and a closed request shows "✓", as the spec's glyph reads everywhere.
 */
@Composable
internal fun RequestRing(
    request: PaymentRequest,
    now: Instant,
    size: Dp,
    modifier: Modifier = Modifier,
    paid: Boolean = false,
) {
    val status = request.resolvedStatus(now)
    val ended = status != PaymentRequestStatus.Open
    val fraction = if (status == PaymentRequestStatus.Completed) 1f else request.split?.paidFraction ?: 0f
    val accent by animateColorAsState(
        if (ended) CodeTheme.colors.textSecondary else CodeTheme.colors.success,
        RequestMotion.change(),
        label = "ring accent",
    )
    val sweep by animateFloatAsState(fraction, RequestMotion.change(), label = "ring sweep")
    val track = CodeTheme.colors.divider
    val inset = size * (4f / 26f)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (sweep > 0f) {
                drawArc(accent, -90f, 360f * sweep.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke))
            }
            drawCircle(accent, radius = this.size.minDimension / 2 - inset.toPx())
        }
        Crossfade(targetState = status == PaymentRequestStatus.Closed, animationSpec = RequestMotion.change(), label = "ring glyph") { closed ->
            Text(
                text = if (closed) "✓" else "$",
                style = if (size > 30.dp) CodeTheme.typography.textMedium else CodeTheme.typography.caption,
                color = Color.Black,
            )
        }
        PaidBadge(glyph = size, ended = ended, paid = paid)
    }
}

/**
 * "<N> requests, <title>, <viewer status>, <paid> of <requested> paid, <status line>", the viewer
 * status left out with no share; or, on a share still owed, "You owe <share> for <title>, <status
 * line>".
 */
@Composable
private fun deckLabel(deck: List<PaymentRequest>, now: Instant, viewerId: ID?): String {
    val front = deck.first()
    val split = front.split
    val share = front.unpaidShareOf(viewerId, now)
    val standing = front.viewerStanding(viewerId, now).description()
    val main = if (share != null) stringResource(
        R.string.cd_requestOwed,
        share.amount.requestText(),
        front.deckTitle.orEmpty(),
        front.statusLine(now).text(),
    ) else if (standing != null) stringResource(
        R.string.cd_requestDeckViewer,
        front.deckTitle.orEmpty(),
        standing,
        split?.paid?.requestText().orEmpty(),
        split?.requested?.requestText().orEmpty(),
        front.statusLine(now)?.text().orEmpty(),
    ) else stringResource(
        R.string.cd_requestDeck,
        front.deckTitle.orEmpty(),
        split?.paid?.requestText().orEmpty(),
        split?.requested?.requestText().orEmpty(),
        front.statusLine(now)?.text().orEmpty(),
    )
    if (deck.size < 2) return main
    return pluralStringResource(R.plurals.cd_requestDeckCount, deck.size, deck.size) + ", " + main
}

/**
 * The deck expanded over the transcript (concept node 12625:1318): every request as its timeline
 * card at full width, in deck order, over a dimmed chat. The first card is the front card moved
 * down from the stack. Collapses on a tap on the dimmed chat, a drag up, or back.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ExpandedRequests(
    deck: List<PaymentRequest>,
    now: Instant,
    viewerId: ID?,
    expanded: Boolean,
    transition: Transition<DeckPlacement>,
    top: Dp,
    closeAnchor: Rect?,
    onCollapse: () -> Unit,
    onOpen: (PaymentRequest) -> Unit,
) {
    BackHandler(enabled = expanded, onBack = onCollapse)
    // The chat behind goes under its backdrop (chatBackdrop, on the scaffold); this only takes the
    // tap that collapses, over the whole screen, the bar's back button and title included. Held
    // through the collapse, so a tap mid-way lands here rather than on the bar behind.
    transition.AnimatedVisibility(visible = { it == DeckPlacement.Expanded }, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onCollapse() } },
        )
    }
    transition.AnimatedVisibility(
        visible = { it == DeckPlacement.Expanded && deck.isNotEmpty() },
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.padding(top = top),
    ) {
        val transition = LocalDeckTransition.current
        val visibility: AnimatedVisibilityScope = this
        val density = LocalDensity.current
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                // Between the cards is outside them too; a card's own click consumes its taps.
                .pointerInput(Unit) { detectTapGestures { onCollapse() } }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { },
                    onDragStopped = { velocity -> if (velocity < -with(density) { 300.dp.toPx() }) onCollapse() },
                ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x2,
            ),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            itemsIndexed(deck, key = { _, request -> request.id.hashCode() }) { index, request ->
                val title = request.deckTitle
                val card = Modifier
                    .fillMaxWidth()
                    .then(
                        if (index == 0 && transition != null) with(transition) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(sharedKey(request.id, "card")),
                                animatedVisibilityScope = visibility,
                                clipInOverlayDuringTransition = OverlayClip(CARD_SHAPE),
                            )
                        } else with(visibility) {
                            // The others follow it in from beneath.
                            Modifier.animateEnterExit(
                                enter = slideInVertically { -it * (index + 1) } + fadeIn(),
                                exit = slideOutVertically { -it * (index + 1) } + fadeOut(),
                            )
                                // A request leaving fades out and the cards below close the gap on the
                                // deck's settle spring.
                                .animateItem(fadeInSpec = null, fadeOutSpec = RequestMotion.change(), placementSpec = SETTLE_SPRING_OFFSET)

                        }
                    )
                    // surfaceVariant is translucent; the opaque base keeps the transcript from
                    // showing through.
                    .background(CodeTheme.colors.background, CARD_SHAPE)
                    .background(CodeTheme.colors.surfaceVariant, CARD_SHAPE)
                    .clickable { onOpen(request) }
                    .padding(CodeTheme.dimens.grid.x3)
                val front = index == 0
                val detail = if (front) Modifier.expandedOnly(visibility) else Modifier
                PaymentRequestCardContent(
                    request = request,
                    title = title,
                    now = now,
                    modifier = card,
                    glyphModifier = if (front) Modifier.deckShared(request.id, "glyph", visibility, CircleShape) else Modifier,
                    titleModifier = if (front) Modifier.deckShared(request.id, "title", visibility) else Modifier,
                    amountsModifier = if (front) Modifier.deckShared(request.id, "amounts", visibility) else Modifier,
                    detailModifier = detail,
                    standing = request.viewerStanding(viewerId, now),
                )
            }
        }
    }
    // In the app bar's trailing slot, above the backdrop: in with it, and out first on collapse.
    // Remove this call to drop it.
    DeckCloseButton(expanded = expanded, anchor = closeAnchor, onCollapse = onCollapse)
}

/**
 * The expanded deck's close button, placed at [anchor] (the app bar's trailing slot, in the
 * window): its end on the anchor's end, centred on it vertically.
 */
@Composable
private fun DeckCloseButton(expanded: Boolean, anchor: Rect?, onCollapse: () -> Unit) {
    anchor ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(DETAIL_FADE_OUT_MS)),
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        x = (anchor.right - origin.x).roundToInt() - placeable.width,
                        y = (anchor.center.y - origin.y).roundToInt() - placeable.height / 2,
                    )
                }
            },
        ) {
            // The same capsule Request details closes with.
            ActionCapsule {
                ActionCapsuleButton(
                    icon = Icons.Filled.Close,
                    description = stringResource(R.string.cd_close),
                    onClick = onCollapse,
                    tag = "request_deck_close",
                )
            }
        }
    }
}

/**
 * For the pieces only the expanded deck shows: they come in once the card is most of the way
 * open, and go first on collapse, so none of them is drawn squeezed into the stack's bounds.
 */
private fun Modifier.expandedOnly(scope: AnimatedVisibilityScope): Modifier = with(scope) {
    this@expandedOnly.animateEnterExit(
        enter = fadeIn(tween(DETAIL_FADE_IN_MS, delayMillis = DETAIL_DELAY_MS)),
        exit = fadeOut(tween(DETAIL_FADE_OUT_MS)),
    )
}

private val CARD_SHAPE = RoundedCornerShape(12.dp)
private val STRIP_SHAPE = RoundedCornerShape(20.dp)
private val STRIP_HEIGHT = 64.dp
// Each card behind steps in this far on both sides, and shows this far below the one in front.
private val STRIP_INSET = 8.dp
private val STRIP_STEP = 6.dp
private val STRIP_RING_SIZE = 40.dp
private val PAY_SHAPE = RoundedCornerShape(50)
private const val APPEAR_MILLIS = 250
private const val APPEAR_SCALE = 0.95f

/** The stack's fade as the front card moves to and from the expanded list. */
private const val STRIP_MILLIS = 300
private const val CYCLE_THRESHOLD = 0.4f
private const val FLING_VELOCITY = 1_000f
private const val DROP_SCALE = 0.06f
// How far past one card height the front card can be pulled, as a fraction of it.
private const val RUBBER_BAND = 0.25f
// The elastic pull upward, or with one request: how far it gives, as a fraction of the card's
// height, and how much of the finger's travel it follows.
internal const val ELASTIC_PULL = 0.25f
private const val ELASTIC_RESISTANCE = 0.3f
internal const val DECK_TAG = "request_deck"

/** The deck's settle motion; the cards closing the gap a leaving request left slide on it too. */
private const val SETTLE_STIFFNESS = Spring.StiffnessMediumLow
private val SETTLE_SPRING_OFFSET = spring<androidx.compose.ui.unit.IntOffset>(stiffness = SETTLE_STIFFNESS)
private val DECK_SPRING = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)


/** A card's scale at [depth] in the stack: each slot back steps in by [insetPx] on both sides. */
private fun slotScale(depth: Float, insetPx: Float, width: Float): Float =
    if (width == 0f) 1f else 1f - 2f * insetPx * depth / width

/** A card's offset at [depth], scaled about its centre, so each slot back shows [stepPx] below the one in front. */
private fun slotY(depth: Float, scale: Float, stepPx: Float, height: Float): Float =
    stepPx * depth + (1f - scale) * height / 2f

// The card's bounds spring settles in about 300ms; the details start at 60% of that.
private const val DETAIL_DELAY_MS = 180
private const val DETAIL_FADE_IN_MS = 120
private const val DETAIL_FADE_OUT_MS = 90
