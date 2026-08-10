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
 * **sticks per-card** on scroll: as a card scrolls above the viewport top it pins into a growing
 * deck at the top ([collapsedReveal] per pinned card) while the cards below stay fanned and readable.
 *
 * The stack's measured height is always the *fanned* height, so the enclosing list scrolls stably
 * (cards are only repositioned, never resized — no feedback into the scroll range). Each card sits
 * at `max(fannedY, pinnedY)`, a continuous transition from fanned to pinned with no jump.
 *
 * [scrolledPast] = how many px the stack's top has scrolled above the viewport top (`-itemOffset`);
 * a lambda so the layout re-reads it on scroll without recomposing the whole stack. Cards are drawn
 * front-to-back so the last (highest-value) card sits on top.
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
        // [pinInset] holds the pinned deck below any top chrome (e.g. the status bar) once cards stick.
        val pinInsetPx = pinInset.roundToPx()
        // Not clamped to ≥0: a negative value (stack below the pin line) keeps cards fanned flush.
        val past = scrolledPast()
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val cardPx = placeables.firstOrNull()?.height ?: 0
        // Always the fanned height, so the list scroll range is stable while cards pin.
        val height = if (placeables.isEmpty()) 0 else cardPx + fannedPx * (placeables.size - 1)
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val fannedY = index * fannedPx
                val pinnedY = (past + pinInsetPx + index * collapsedPx).toInt()
                placeable.placeRelative(0, maxOf(fannedY, pinnedY))
            }
        }
    }
}