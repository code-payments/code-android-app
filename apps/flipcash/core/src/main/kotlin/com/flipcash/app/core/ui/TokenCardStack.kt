package com.flipcash.app.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import kotlin.collections.forEach

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
 */
@Composable
fun TokenCardStack(
    tokens: List<TokenWithLocalizedBalance>,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 224.dp,
    fannedReveal: Dp = 64.dp,
    collapsedReveal: Dp = 12.dp,
    pinInset: Dp = 0.dp,
    scrolledPast: () -> Float = { 0f },
    onCardClick: (TokenWithLocalizedBalance) -> Unit = {},
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            tokens.forEach { token ->
                TokenCard(
                    tokenWithBalance = token,
                    height = cardHeight,
                    onClick = { onCardClick(token) },
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
        val collapseComplete =
            ((placeables.size - 1) * (fannedPx - collapsedPx) - pinInsetPx).coerceAtLeast(0)
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