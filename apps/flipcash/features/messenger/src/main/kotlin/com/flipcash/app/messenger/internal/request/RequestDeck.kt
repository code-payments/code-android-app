package com.flipcash.app.messenger.internal.request

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
    visibility: AnimatedVisibilityScope,
    shape: androidx.compose.ui.graphics.Shape? = null,
): Modifier {
    val transition = LocalDeckTransition.current ?: return this
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

/**
 * The app-bar deck (concept B): the front request with up to two cards peeking beneath it. Swipe
 * down cycles; tap expands. Hidden while [expanded], when the front card is over the transcript.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RequestDeckPill(
    deck: List<PaymentRequest>,
    now: Instant,
    expanded: Boolean,
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
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val front = shown.firstOrNull() ?: return@AnimatedVisibility
            val transition = LocalDeckTransition.current
            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            val drag = remember { Animatable(0f) }
            val pillHeightPx = with(density) { PILL_HEIGHT.toPx() }
            val canCycle = shown.size > 1

            val label = deckLabel(shown, now)
            val nextLabel = stringResource(R.string.action_nextRequest)
            Box(
                modifier = Modifier
                    .padding(bottom = PEEK_STEP * 2)
                    .clearAndSetSemantics {
                        contentDescription = label
                        role = Role.Button
                        onClick { onExpand(); true }
                        if (canCycle) customActions = listOf(CustomAccessibilityAction(nextLabel) { onCycle(); true })
                    },
            ) {
                // The peeking cards, back first. Widths step in so the front card's edges stay clear.
                shown.drop(1).take(2).withIndex().reversed().forEach { (index, _) ->
                    val depth = index + 1
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = PEEK_INSET * depth)
                            .offset(y = PEEK_STEP * depth)
                            .background(CodeTheme.colors.background, PILL_SHAPE)
                            .background(
                                if (depth == 1) CodeTheme.colors.surfaceVariant else CodeTheme.colors.surface,
                                PILL_SHAPE,
                            )
                            .border(1.dp, CodeTheme.colors.border, PILL_SHAPE),
                    )
                }
                DeckFront(
                    request = front,
                    now = now,
                    visibility = this@AnimatedVisibility,
                    modifier = Modifier
                        .then(
                            if (transition != null) with(transition) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(sharedKey(front.id, "card")),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    clipInOverlayDuringTransition = OverlayClip(PILL_SHAPE),
                                )
                            } else Modifier
                        )
                        .offset { IntOffset(0, drag.value.roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                scope.launch {
                                    // One request: a small elastic pull and nothing more.
                                    val limit = if (canCycle) pillHeightPx * 1.5f else pillHeightPx * 0.25f
                                    drag.snapTo((drag.value + delta * if (canCycle) 1f else 0.3f).coerceIn(0f, limit))
                                }
                            },
                            onDragStopped = { velocity ->
                                val past = drag.value > pillHeightPx * CYCLE_THRESHOLD || velocity > FLING_VELOCITY
                                if (canCycle && past) {
                                    onCycle()
                                    drag.snapTo(0f)
                                } else {
                                    drag.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            },
                        )
                        .clickable(onClick = onExpand),
                )
            }
        }
    }
}

@Composable
private fun DeckFront(
    request: PaymentRequest,
    now: Instant,
    visibility: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val ended = !request.isOpen(now)
    val split = request.split
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
            size = 26.dp,
            modifier = Modifier.deckShared(request.id, "glyph", visibility, CircleShape),
        )
        Column {
            Text(
                text = request.deckTitle.orEmpty(),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.deckShared(request.id, "title", visibility),
            )
            if (split != null) {
                Text(
                    text = stringResource(
                        R.string.label_requestDeckProgress,
                        split.paid.requestText(),
                        split.requested.requestText(),
                    ),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.deckShared(request.id, "amounts", visibility),
                )
            }
        }
    }
}

/**
 * The paid fraction as a ring around a "$" glyph, filled clockwise from 12 o'clock. Grey once
 * the request has ended; full once it completed; a closed request shows "✓".
 */
@Composable
internal fun RequestRing(request: PaymentRequest, now: Instant, size: Dp, modifier: Modifier = Modifier) {
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
    }
}

/** "<N> requests, <title>, <paid> of <requested> paid, <status line>". */
@Composable
private fun deckLabel(deck: List<PaymentRequest>, now: Instant): String {
    val front = deck.first()
    val split = front.split
    val main = stringResource(
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
    expanded: Boolean,
    top: Dp,
    onCollapse: () -> Unit,
    onOpen: (PaymentRequest) -> Unit,
) {
    BackHandler(enabled = expanded, onBack = onCollapse)
    // The chat behind goes under its backdrop (chatBackdrop, on the scaffold); this only takes the
    // tap that collapses, over the whole screen, the bar's back button and title included. Held
    // through the collapse, so a tap mid-way lands here rather than on the bar behind.
    AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onCollapse() } },
        )
    }
    AnimatedVisibility(
        visible = expanded && deck.isNotEmpty(),
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
                )
            }
            item(key = "handle") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.expandedOnly(visibility)
                            .size(width = 36.dp, height = 5.dp)
                            .background(CodeTheme.colors.textSecondary, RoundedCornerShape(2.5.dp))
                            .clickable(onClick = onCollapse),
                    )
                }
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
private const val APPEAR_MILLIS = 250
private const val APPEAR_SCALE = 0.95f
private val PEEK_STEP = 5.dp
private val PEEK_INSET = 7.dp
private const val ENDED_ALPHA = 0.4f
private const val CYCLE_THRESHOLD = 0.4f
private const val FLING_VELOCITY = 1_000f

// The card's bounds spring settles in about 300ms; the details start at 60% of that.
private const val DETAIL_DELAY_MS = 180
private const val DETAIL_FADE_IN_MS = 120
private const val DETAIL_FADE_OUT_MS = 90
