package com.flipcash.app.messenger.internal.request

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import com.flipcash.shared.chat.ui.ActionCapsule
import com.flipcash.shared.chat.ui.ActionCapsuleButton
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
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
 * The shared-element scope the deck pill (in the app bar) and the expanded list (over the
 * transcript) share, so the front card can move between them. Null outside [RequestDeckLayout].
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalDeckTransition = compositionLocalOf<SharedTransitionScope?> { null }

/** Hosts the screen so the deck's front card can move between the app bar and the expanded list. */
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
 * Text scales to its new bounds, since the pill and the card set it in different styles.
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

/** Where the deck sits and how loud it is. */
internal enum class DeckPlacement {
    /** The grey pill in the app bar: the viewer owes nothing, or the strip has collapsed into it. */
    Pill,

    /** The full-width card under the app bar: the viewer owes. */
    Strip,

    /** Every request as a card over the transcript. */
    Expanded,
}

/**
 * The app-bar deck (concept B): the front request with up to two cards peeking beneath it. Swipe
 * down cycles; tap expands. Shown while [transition] is at the pill; the front card moves from
 * there to the strip and the expanded list as one shared element.
 */
@Composable
internal fun RequestDeckPill(
    deck: List<PaymentRequest>,
    now: Instant,
    viewerId: ID?,
    transition: Transition<DeckPlacement>,
    onExpand: () -> Unit,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held through the exit, so the pill doesn't empty as it fades out.
    var shown by remember { mutableStateOf(deck) }
    if (deck.isNotEmpty()) shown = deck
    // Arriving and leaving: a fade and a slight scale in place, while the width eases so the title
    // makes room. A deck that is there when the chat opens starts visible, so it doesn't fade.
    AnimatedVisibility(
        visible = deck.isNotEmpty(),
        enter = fadeIn(tween(APPEAR_MILLIS)) +
            scaleIn(tween(APPEAR_MILLIS), initialScale = APPEAR_SCALE) +
            expandHorizontally(tween(APPEAR_MILLIS), expandFrom = Alignment.End, clip = false),
        exit = fadeOut(tween(APPEAR_MILLIS)) +
            scaleOut(tween(APPEAR_MILLIS), targetScale = APPEAR_SCALE) +
            shrinkHorizontally(tween(APPEAR_MILLIS), shrinkTowards = Alignment.End, clip = false),
        modifier = modifier,
    ) {
        DeckStack(
            deck = shown,
            now = now,
            viewerId = viewerId,
            style = DeckPlacement.Pill,
            transition = transition,
            onExpand = onExpand,
            onCycle = onCycle,
            onPay = {},
        )
    }
}

/**
 * The deck as a full-width card under the app bar (concept node 12670:1262), shown while the
 * viewer owes and the timeline is at its newest messages. It keeps its room while [expanded], so
 * the timeline doesn't move under the expanded cards, and gives it up as it leaves for the pill.
 */
@Composable
internal fun RequestDeckStrip(
    deck: List<PaymentRequest>,
    now: Instant,
    viewerId: ID?,
    transition: Transition<DeckPlacement>,
    rest: DeckPlacement,
    onExpand: () -> Unit,
    onCycle: () -> Unit,
    onPay: (PaymentRequest) -> Unit,
    onHeightChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(deck) }
    if (deck.isNotEmpty()) shown = deck
    // Linear, so a scroll that seeks it into the pill takes its height off at the scroll's pace.
    transition.AnimatedVisibility(
        visible = {
            deck.isNotEmpty() &&
                (it == DeckPlacement.Strip || it == DeckPlacement.Expanded && rest == DeckPlacement.Strip)
        },
        enter = expandVertically(tween(STRIP_MILLIS, easing = LinearEasing), expandFrom = Alignment.Top),
        exit = shrinkVertically(tween(STRIP_MILLIS, easing = LinearEasing), shrinkTowards = Alignment.Top),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                // Its whole height, padding included: the scroll that collapses it into the pill.
                .onSizeChanged { onHeightChange(it.height) }
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x2)
                .height(DeckStyle.Strip.height + DeckStyle.Strip.step * 2),
        ) {
            DeckStack(
                deck = shown,
                now = now,
                viewerId = viewerId,
                style = DeckPlacement.Strip,
                transition = transition,
                onExpand = onExpand,
                onCycle = onCycle,
                onPay = onPay,
            )
        }
    }
}

/** The stack's measurements in each placement. */
private class DeckStyle(
    val height: Dp,
    val shape: RoundedCornerShape,
    val inset: Dp,
    val step: Dp,
) {
    companion object {
        val Pill = DeckStyle(height = 43.dp, shape = RoundedCornerShape(18.dp), inset = 7.dp, step = 5.dp)
        val Strip = DeckStyle(height = 64.dp, shape = RoundedCornerShape(20.dp), inset = 8.dp, step = 6.dp)
    }
}

private val DeckPlacement.deckStyle: DeckStyle
    get() = if (this == DeckPlacement.Strip) DeckStyle.Strip else DeckStyle.Pill

/**
 * The front request with up to two cards peeking beneath it, in one of the deck's placements.
 * Swipe down cycles; tap expands. Shown while [transition] is at [style], whose scope its front
 * card moves between placements in.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DeckStack(
    deck: List<PaymentRequest>,
    now: Instant,
    viewerId: ID?,
    style: DeckPlacement,
    transition: Transition<DeckPlacement>,
    onExpand: () -> Unit,
    onCycle: () -> Unit,
    onPay: (PaymentRequest) -> Unit,
) {
    transition.AnimatedVisibility(
        visible = { it == style },
        enter = fadeIn(tween(STRIP_MILLIS)),
        exit = fadeOut(tween(STRIP_MILLIS)),
    ) {
        val front = deck.firstOrNull() ?: return@AnimatedVisibility
        val geometry = style.deckStyle
        val shape = geometry.shape
        val strip = style == DeckPlacement.Strip
        val transition = LocalDeckTransition.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val drag = remember { Animatable(0f) }
        val arrive = remember { Animatable(1f) }
        // Where the swipe was let go: how far the front card had travelled, and how far through
        // the swipe that was. The hand-over starts every card from there.
        var releaseDrag by remember { mutableFloatStateOf(0f) }
        var releaseProgress by remember { mutableFloatStateOf(1f) }
        val pillHeightPx = with(density) { geometry.height.toPx() }
        val canCycle = deck.size > 1
        val pillWidth = remember(scope) { PillWidth(scope) }

        val label = deckLabel(deck, now, viewerId, strip)
        val nextLabel = stringResource(R.string.action_nextRequest)
        // The strip's Pay is a button of its own, so the deck's label goes on the rest of its card.
        val deckSemantics = Modifier.clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            onClick { onExpand(); true }
            if (canCycle) customActions = listOf(CustomAccessibilityAction(nextLabel) { onCycle(); true })
        }
        Box(
            modifier = Modifier
                .padding(bottom = geometry.step * 2)
                .then(if (strip) Modifier else deckSemantics),
        ) {
            // How far the front card is through being swiped to the back, 0 to 1. The cards behind
            // rise a slot with it, so the next request is already in place when the swipe lands.
            val progress = (drag.value / pillHeightPx).coerceIn(0f, 1f)
            val insetPx = with(density) { geometry.inset.toPx() }
            val stepPx = with(density) { geometry.step.toPx() }
            // The card just swiped off, travelling from below the pill to its slot at the back.
            val swiped = if (canCycle && arrive.value < 1f) deck.last() else null
            val swipedDepth = (deck.size - 1).coerceAtMost(2).toFloat()
            // How far each card still has to rise after a hand-over: the swipe was let go short of
            // a full pill height, so the cards behind finish the step on the same spring.
            val handover = (1f - releaseProgress) * (1f - arrive.value)

            @Composable
            fun Card(request: PaymentRequest, layer: GraphicsLayerScope.() -> Unit, depth: Float, content: Boolean) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer(layer)
                        // The request rising from behind is wider or narrower than the pill until
                        // the swipe is let go; it shows clipped to the card until then.
                        .clip(shape)
                        .background(CodeTheme.colors.background, shape)
                        .background(peekColor(style, depth), shape)
                        .border(1.dp, CodeTheme.colors.border, shape),
                ) {
                    if (content) {
                        DeckCardFront(
                            request = request,
                            now = now,
                            viewerId = viewerId,
                            style = style,
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
            DeckCardFront(
                request = front,
                now = now,
                viewerId = viewerId,
                style = style,
                visibility = this@AnimatedVisibility,
                onPay = { onPay(front) },
                bodyModifier = if (strip) deckSemantics else Modifier,
                modifier = Modifier
                    .then(if (strip) Modifier.fillMaxWidth() else pillWidth.modifier)
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
                                val value = drag.value
                                val next = if (!canCycle) {
                                    // One request: a small elastic pull and nothing more.
                                    (value + delta * 0.3f).coerceIn(0f, pillHeightPx * 0.25f)
                                } else if (delta > 0f && value >= pillHeightPx) {
                                    // Past one pill height the card rubber-bands instead of
                                    // sliding on into the chat.
                                    val over = (value - pillHeightPx) / (pillHeightPx * RUBBER_BAND)
                                    value + delta * 0.5f * (1f - over).coerceAtLeast(0f)
                                } else {
                                    (value + delta).coerceAtLeast(0f)
                                }
                                drag.snapTo(next)
                            }
                        },
                        onDragStopped = { velocity ->
                            val past = drag.value > pillHeightPx * CYCLE_THRESHOLD || velocity > FLING_VELOCITY
                            if (canCycle && past) {
                                // Hand over on release: the card that rose becomes the front and the
                                // swiped one drops behind to the back, each from where it was, while
                                // the pill eases to the new front's width on the same spring.
                                releaseDrag = drag.value
                                releaseProgress = (drag.value / pillHeightPx).coerceIn(0f, 1f)
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
private fun peekColor(style: DeckPlacement, depth: Float): Color {
    val back = (depth - 1f).coerceIn(0f, 1f)
    return when (style) {
        DeckPlacement.Pill, DeckPlacement.Expanded -> lerp(CodeTheme.colors.surfaceVariant, CodeTheme.colors.surface, back)
        DeckPlacement.Strip -> lerp(CodeTheme.colors.surfaceVariant, Color.White.copy(alpha = 0.08f), back)
    }
}

/** The front card's face in [style]. [bodyModifier] goes on everything but the strip's Pay button. */
@Composable
private fun DeckCardFront(
    request: PaymentRequest,
    now: Instant,
    viewerId: ID?,
    style: DeckPlacement,
    visibility: AnimatedVisibilityScope?,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
) {
    when (style) {
        DeckPlacement.Pill, DeckPlacement.Expanded -> DeckFront(request = request, now = now, viewerId = viewerId, visibility = visibility, modifier = modifier)
        DeckPlacement.Strip -> StripFront(request, now, viewerId, visibility, onPay, modifier, bodyModifier)
    }
}

/**
 * The strip's front card (concept node 12670:1262): glyph, title, "You owe <share> · <time left>"
 * and Pay; or the viewer's status line and no Pay on a card the viewer doesn't owe on.
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
    val shape = DeckStyle.Strip.shape
    val share = request.unpaidShareOf(viewerId, now)
    val standing = request.viewerStanding(viewerId, now)
    val ended = !request.isOpen(now)
    Row(
        modifier = modifier
            .height(DeckStyle.Strip.height)
            .background(CodeTheme.colors.background, shape)
            .alpha(if (ended) ENDED_ALPHA else 1f)
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

@Composable
private fun DeckFront(
    request: PaymentRequest,
    now: Instant,
    viewerId: ID?,
    visibility: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val ended = !request.isOpen(now)
    val split = request.split
    val standing = request.viewerStanding(viewerId, now)
    Row(
        modifier = modifier
            .background(CodeTheme.colors.background, PILL_SHAPE)
            .alpha(if (ended) ENDED_ALPHA else 1f)
            .background(CodeTheme.colors.surfaceVariant, PILL_SHAPE)
            .border(1.dp, CodeTheme.colors.border, PILL_SHAPE)
            .padding(
                start = CodeTheme.dimens.grid.x1,
                end = CodeTheme.dimens.grid.x2,
                top = CodeTheme.dimens.grid.x1,
                bottom = CodeTheme.dimens.grid.x1,
            )
            .widthIn(max = PILL_MAX_WIDTH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        RequestRing(
            request = request,
            now = now,
            size = RING_SIZE,
            paid = standing is ViewerStanding.Paid,
            modifier = Modifier
                .deckShared(request.id, "glyph", visibility, CircleShape),
        )
        // The text room the widest pill leaves, so a title ellipsizes only at that width and not
        // while the pill eases open around it.
        val textMax = PILL_MAX_WIDTH - RING_SIZE - CodeTheme.dimens.grid.x2
        Column {
            Text(
                text = request.deckTitle.orEmpty(),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .measuredUpTo(textMax)
                    .deckShared(request.id, "title", visibility),
            )
            if (split != null) {
                ViewerStatusLine(
                    request = request,
                    standing = standing,
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    modifier = Modifier
                        .measuredUpTo(textMax)
                        .deckShared(request.id, "amounts", visibility),
                )
            }
        }
    }
}

/**
 * The deck's width, sprung to each new front's natural width on the spring the cards move on, so
 * the pill and every card stretched to it ease together from the moment a swipe is let go. The
 * front surface draws at the eased width and its content clips inside it.
 */
private class PillWidth(private val scope: CoroutineScope) {
    private val width = Animatable(0f)
    private var target = 0

    val modifier: Modifier = Modifier.layout { measurable, constraints ->
        val natural = constraints.constrainWidth(measurable.maxIntrinsicWidth(constraints.maxHeight))
        if (natural != target) {
            val first = target == 0
            target = natural
            scope.launch {
                if (first) width.snapTo(natural.toFloat()) else width.animateTo(natural.toFloat(), DECK_SPRING)
            }
        }
        val current = if (width.value == 0f) natural else width.value.roundToInt()
        val placeable = measurable.measure(constraints.copy(minWidth = current, maxWidth = current))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * Measures the content as if [max] wide were free, then reports no more than the room it has, so
 * a container easing open clips the content rather than re-wrapping or ellipsizing it each frame.
 */
private fun Modifier.measuredUpTo(max: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = max.roundToPx()))
    layout(placeable.width.coerceAtMost(constraints.maxWidth), placeable.height) {
        placeable.placeRelative(0, 0)
    }
}

/**
 * The paid fraction as a ring around a "$" glyph, filled clockwise from 12 o'clock. Grey once
 * the request has ended; full once it completed; a closed request shows "✓".
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
    val accent = if (ended) CodeTheme.colors.textSecondary else CodeTheme.colors.success
    val track = CodeTheme.colors.divider
    val inset = size * (4f / 26f)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (fraction > 0f) {
                drawArc(accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke))
            }
            drawCircle(accent, radius = this.size.minDimension / 2 - inset.toPx())
        }
        Text(
            text = if (status == PaymentRequestStatus.Closed) "✓" else "$",
            style = if (size > 30.dp) CodeTheme.typography.textMedium else CodeTheme.typography.caption,
            color = Color.Black,
        )
        if (paid) PaidBadge(glyph = size, ended = ended)
    }
}

/**
 * "<N> requests, <title>, <viewer status>, <paid> of <requested> paid, <status line>", the viewer
 * status left out with no share; or in the [strip], on a share still owed, "You owe <share> for
 * <title>, <status line>".
 */
@Composable
private fun deckLabel(deck: List<PaymentRequest>, now: Instant, viewerId: ID?, strip: Boolean): String {
    val front = deck.first()
    val split = front.split
    val share = front.unpaidShareOf(viewerId, now)?.takeIf { strip }
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
 * down from the app bar. Collapses on a tap on the dimmed chat, a drag up, or back.
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
    pill: Rect?,
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
                        }
                    )
                    // surfaceVariant is translucent; the opaque base keeps the transcript from
                    // showing through, and sits outside the alpha so ended cards stay solid.
                    .background(CodeTheme.colors.background, CARD_SHAPE)
                    .alpha(if (request.isOpen(now)) 1f else ENDED_ALPHA)
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
    // Where the pill sat: a way out at the spot the card came from. Above the backdrop, in with it,
    // and out first on collapse so it is gone before the pill lands back there. Remove this call to
    // drop it.
    DeckCloseButton(expanded = expanded, pill = pill, onCollapse = onCollapse)
}

/**
 * The expanded deck's close button, placed over [pill] (the collapsed pill's last bounds in the
 * window), its end on the pill's end and centred on the front card.
 */
@Composable
private fun DeckCloseButton(expanded: Boolean, pill: Rect?, onCollapse: () -> Unit) {
    pill ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val cardCenter = with(LocalDensity.current) { PILL_HEIGHT.toPx() / 2 }
    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(DETAIL_FADE_OUT_MS)),
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        x = (pill.right - origin.x).roundToInt() - placeable.width,
                        y = (pill.top + cardCenter - origin.y).roundToInt() - placeable.height / 2,
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
 * open, and go first on collapse, so none of them is drawn squeezed into the pill's bounds.
 */
private fun Modifier.expandedOnly(scope: AnimatedVisibilityScope): Modifier = with(scope) {
    this@expandedOnly.animateEnterExit(
        enter = fadeIn(tween(DETAIL_FADE_IN_MS, delayMillis = DETAIL_DELAY_MS)),
        exit = fadeOut(tween(DETAIL_FADE_OUT_MS)),
    )
}

private val PILL_SHAPE = RoundedCornerShape(18.dp)
private val CARD_SHAPE = RoundedCornerShape(12.dp)
private val PILL_HEIGHT = 43.dp
private val PILL_MAX_WIDTH = 150.dp
private val RING_SIZE = 26.dp
private val STRIP_RING_SIZE = 40.dp
private val PAY_SHAPE = RoundedCornerShape(50)
private const val APPEAR_MILLIS = 250

/** The strip's move to and from the pill, which a scroll seeks through. */
private const val STRIP_MILLIS = 300
private const val APPEAR_SCALE = 0.95f
private val PEEK_STEP = 5.dp
private val PEEK_INSET = 7.dp
private const val ENDED_ALPHA = 0.4f
private const val CYCLE_THRESHOLD = 0.4f
private const val FLING_VELOCITY = 1_000f
private const val DROP_SCALE = 0.06f
// How far past one pill height the front card can be pulled, as a fraction of it.
private const val RUBBER_BAND = 0.25f
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
