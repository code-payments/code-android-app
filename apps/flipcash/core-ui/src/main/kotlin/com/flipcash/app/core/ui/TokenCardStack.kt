package com.flipcash.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.solana.keys.Mint

/**
 * A vertical stack of [TokenCard]s that fans out (each card revealing its [fannedReveal] header) and
 * **collapses, then scrolls off**: as the stack scrolls up, cards pin at the top and tighten from the
 * fanned gap to [collapsedReveal] (a growing deck); once fully collapsed the deck **releases** and
 * scrolls off the top with the rest of the list (it does not pin permanently).
 *
 * The measured height is always the *fanned* height, so the enclosing list's scroll range is stable
 * (cards are only repositioned, never resized). [scrolledPast] = px the stack's top has scrolled above
 * the viewport top (`-itemOffset`); it is read in the **placement** phase so scrolling only re-places
 * the cards and never re-measures/re-composes them. [pinInset] holds the collapsing deck below top
 * chrome (e.g. the status bar). Cards are drawn front-to-back so the last (highest-value) sits on top.
 *
 * When [collapsedReveal] is 0 (the default), back cards collapse completely behind the front card —
 * no slivers are visible at rest — matching the iOS wallet card-stack behaviour.
 *
 * ## Card-expand transition
 * Every card carries a mint-keyed [SharedTransition.TokenCard] so that when the currency-info screen
 * is pushed, the card whose mint matches the destination hero flies between the deck slot and the
 * hero (shared-bounds overlay). The tapped card is hosted in the overlay, so it keeps its natural
 * opacity while the rest of the wallet fades.
 *
 * [expandingMint] + [expandProgress] drive the **deck reorganisation** around the tapped card as the
 * push progresses (0 → 1), ported from iOS: cards **above** the tapped one gather onto exactly the spot
 * it lands (collapsing into its slot underneath it), cards **below** run off the bottom, and both fade
 * out linearly. Because each card reads its own current top, this holds at any scroll position. The
 * tapped card itself is skipped here (shared-bounds overlay owns it) and flies to the hero.
 *
 * The flying card is hosted in the transition overlay while **opening** (so it lifts cleanly above the
 * parting deck) but **in-layer** while closing, so on the way back it re-inserts at its natural deck
 * z-order and slides under its neighbours instead of landing on top and snapping under.
 *
 * ## Card arrival
 * [arrivingMint] names a card that has just joined the deck (a claim in a currency the wallet did not
 * hold). It rises [ArrivalRise] into its slot and fades up; the deck's layout is final from the first
 * frame, so nothing around it moves — matching iOS's own `arrivalProgress` effect. [arrivalHeld] keeps
 * it off-stage until the caller is ready, which is how the wallet waits for the bill it was claimed
 * from to leave before the card is seen to land.
 *
 * The caller names the card rather than the stack diffing its own token list, because the case that
 * most needs the animation — a wallet that held nothing, so the stack was not composed at all — is
 * exactly the one a diff cannot see.
 */
@Composable
fun TokenCardStack(
    tokens: List<TokenWithLocalizedBalance>,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 224.dp,
    fannedReveal: Dp = 64.dp,
    collapsedReveal: Dp = 0.dp,
    pinInset: Dp = 0.dp,
    scrolledPast: () -> Float = { 0f },
    expandingMint: Mint? = null,
    expandProgress: () -> Float = { 0f },
    heroTarget: Rect? = null,
    pullOffset: () -> Float = { 0f },
    arrivingMint: Mint? = null,
    arrivalHeld: Boolean = false,
    onCardClick: (TokenWithLocalizedBalance, Rect) -> Unit = { _, _ -> },
) {
    val tappedIndex = remember(expandingMint, tokens) {
        if (expandingMint == null) -1 else tokens.indexOfFirst { it.token.address == expandingMint }
    }
    // Seeded off-stage only when there is a card to let in, so an ordinary deck draws at rest on its
    // first frame instead of rising into place.
    val arrival = remember(arrivingMint) {
        Animatable(if (arrivingMint == null) 1f else 0f)
    }
    LaunchedEffect(arrivingMint, arrivalHeld) {
        if (arrivingMint != null && !arrivalHeld) {
            arrival.animateTo(1f, tween(ArrivalDurationMillis, easing = FastOutSlowInEasing))
        }
    }

    // Screen height, so cards below the selected one travel off the bottom edge (read live in the reorg
    // layer; changes rarely).
    val windowHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }

    Layout(
        modifier = modifier
            .fillMaxWidth(),
        content = {
            tokens.forEachIndexed { index, token ->
                val isTapped = index == tappedIndex
                val isArriving = token.token.address == arrivingMint
                val cardBounds = remember(token.token.address) { mutableStateOf(Rect.Zero) }
                TokenCard(
                    tokenWithBalance = token,
                    modifier = Modifier
                        .onGloballyPositioned { cardBounds.value = it.boundsInWindow() }
                        // Deck reorganisation. The tapped card FLIES to the expanded hero frame in step
                        // with the overlay's own hero, so the two coincide — the overlay draws the crisp
                        // card on top, while this one stays at its natural deck z-order (under its
                        // neighbours) and carries the hand-off on collapse without a z snap. The other
                        // cards part around it: above slide off the top, below off the bottom, dissolving.
                        .graphicsLayer {
                            if (isArriving && arrival.value < 1f) {
                                // Rises into its slot and fades up, on its own — the deck around it
                                // is already laid out where it will end. Ahead of the expand
                                // branches, as on iOS: a card still arriving cannot also be the one
                                // being opened.
                                translationY = ArrivalRise.toPx() * (1f - arrival.value)
                                alpha = arrival.value
                            } else if (isTapped) {
                                val src = cardBounds.value
                                val tgt = heroTarget
                                val p = expandProgress()
                                if (tgt != null && tgt.width > 0f && src.width > 0f) {
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    val scale = lerp(1f, tgt.width / src.width, p)
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = (tgt.left - src.left) * p
                                    // Match the overlay hero's pull-to-close translation so the two stay
                                    // coincident (no second card during a pull or the release).
                                    translationY = (tgt.top - src.top) * p + pullOffset()
                                }
                                // Opacity backing for the overlay hero's cross-fade. It reaches full
                                // opacity well before the slot (so it's a solid, opaque backing under the
                                // fading overlay hero — no mid-transition dip where the dark background
                                // bleeds through), yet is fully hidden at the expanded frame (p→1) so it
                                // never doubles the overlay hero while expanded or during a pull-to-close.
                                alpha = ((1f - p) * 2.5f).coerceIn(0f, 1f)
                            } else if (tappedIndex >= 0) {
                                val hp = expandProgress()
                                val tgt = heroTarget
                                if (hp > 0f && tgt != null) {
                                    // Deck reorganisation, ported from iOS `TokenCardStack.visualEffect`.
                                    // Every non-hero card interpolates LINEARLY from its rest position to a
                                    // "cleared" spot and fades out (opacity 1 → 0) over the SAME progress that
                                    // flies the hero — so the whole thing reads as one animation.
                                    //
                                    //  • ABOVE the opened card: gather onto EXACTLY the top the opened card lands
                                    //    at (`tgt.top`) — every above-card converges on that one spot, collapsing
                                    //    into its slot UNDERNEATH it (they share the hero's z-order-below position).
                                    //  • BELOW: run off the bottom edge.
                                    //
                                    // Each card reads its OWN current top (`cardBounds`), which is what makes this
                                    // correct at ANY scroll position: when the deck is scrolled so the cards above
                                    // the opened one are collapsed/pinned at the top, they simply converge into the
                                    // opened card from wherever they are — they never fan DOWN into view. And the
                                    // cards converging from their fanned spread onto one point IS the fan tightening
                                    // closed (and, in reverse, breathing back open on reinsert) — no explicit
                                    // per-card slivers needed. A single linear fade avoids any bright-then-covered
                                    // "reveal": they are already dissolving as they gather.
                                    val clearedTop = if (index < tappedIndex) tgt.top else windowHeightPx
                                    translationY = (clearedTop - cardBounds.value.top) * hp
                                    alpha = 1f - hp
                                }
                            }
                        },
                    height = cardHeight,
                    onClick = { onCardClick(token, cardBounds.value) },
                )
            }
        },
    ) { measurables, constraints ->
        val fannedPx = fannedReveal.roundToPx()
        val collapsedPx = collapsedReveal.roundToPx()
        val pinInsetPx = pinInset.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val cardPx = placeables.firstOrNull()?.height ?: 0
        // Always the fanned height, so the list's scroll range is stable while cards collapse.
        val height = if (placeables.isEmpty()) 0 else cardPx + fannedPx * (placeables.size - 1)
        // Scroll distance at which every card has finished collapsing (the last card pins last).
        // Deliberately NOT clamped at 0: it is a cap on `past`, and at that cap the last card sits at
        // exactly its fanned slot, so the deck never leaves the measured height. When the fanned slack
        // is smaller than the pin inset — a single card has none at all — the cap is negative and no
        // card ever pins, which is correct: there is nothing to collapse. Clamping it to 0 would let
        // the deck pin `pinInset` px below its own top, pushing the front card past the bottom of the
        // item and under the following row (the wallet's "Recent" section overlapping a lone card).
        val collapseComplete = (placeables.size - 1) * (fannedPx - collapsedPx) - pinInsetPx
        layout(constraints.maxWidth, height) {
            // Read scroll offset HERE (placement) — not in the measure scope — so scrolling only
            // re-places the cards; reading it while measuring would re-run each card's SubcomposeLayout.
            // Cap only the UPPER bound at collapseComplete: once fully collapsed the deck stops pinning
            // and the frozen layout scrolls off with the list. The value is intentionally allowed to go
            // negative — at rest the stack sits below the top chrome, and that negative keeps `pinnedY`
            // under `fannedY` so every card (including the last) stays fanned instead of collapsing.
            val past = scrolledPast().coerceAtMost(collapseComplete.toFloat())
            placeables.forEachIndexed { index, placeable ->
                val fannedY = index * fannedPx
                val pinnedY = (past + pinInsetPx + index * collapsedPx).toInt()
                placeable.placeRelative(0, maxOf(fannedY, pinnedY))
            }
        }
    }
}

/** How far below its slot an arriving card starts. Matches iOS's `arrivalRise`. */
private val ArrivalRise = 40.dp

/** How long a newly-claimed card takes to rise into the deck. Matches iOS's own arrival. */
private const val ArrivalDurationMillis = 900
