package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.flipcash.shared.chat.reactions.ReactionStrip
import androidx.compose.ui.res.stringResource
import com.flipcash.core.R

// Sizes from the iOS strip (node 9779:105563).
private val StripHeight = 55.dp
private val StripMaxWidth = 313.dp
private val ItemSize = 40.dp
private val ItemSpacing = 4.dp
private val Inset = 8.dp
private val AddSize = 38.dp

/** How far the fade reaches ahead of the "+" before the row draws fully. */
private val FadeLead = 28.dp

/** iOS's pre-glass strip surface. */
private val StripSurface = Color(0xFF303030)

/**
 * The strip a long-press on a reactable, selected bubble shows beside it: up to the 12 entries
 * [ReactionStripComposer][com.flipcash.shared.chat.reactions.ReactionStripComposer] composed,
 * self-reacted ones highlighted, and a "+" pinned at the trailing end that opens the full picker.
 * A tap on an entry both toggles the reaction and clears the selection — [onToggle] is the one
 * signal for both.
 *
 * The capsule grows to fit its entries up to 313dp; past that the emoji scroll under the "+",
 * fading out ahead of it.
 */
@Composable
fun QuickReactionStrip(
    entries: List<ReactionStrip.Entry>,
    onToggle: (emoji: String) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    Box(
        modifier = modifier
            .widthIn(max = StripMaxWidth)
            .height(StripHeight)
            .clip(CircleShape)
            .background(StripSurface),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val density = LocalDensity.current
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val width = size.width
                    val inset = Inset.toPx()
                    val solidUntil = width - inset - AddSize.toPx() - FadeLead.toPx()
                    val clearFrom = width - inset - AddSize.toPx() / 2
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            (inset / width) to Color.Black,
                            (solidUntil / width).coerceAtLeast(inset / width) to Color.Black,
                            (clearFrom / width) to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .testTag("quick_reaction_scroll")
                .horizontalScroll(rememberScrollState())
                // Room to bring the last emoji out from under the "+" and its fade.
                .padding(start = Inset, end = Inset + AddSize + FadeLead),
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { entry ->
                Box(
                    modifier = Modifier
                        .testTag("quick_reaction_${entry.emoji}")
                        .semantics { selected = entry.highlighted }
                        .size(ItemSize)
                        .clip(CircleShape)
                        .background(if (entry.highlighted) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                        .combinedClickable(onClick = { onToggle(entry.emoji) }, onLongClick = {}),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = entry.emoji, fontSize = with(density) { 28.dp.toSp() })
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(end = Inset)
                .testTag("quick_reaction_plus")
                .semantics { contentDescription = "More reactions" }
                .size(AddSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .combinedClickable(onClick = onOpenPicker, onLongClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AddReaction,
                contentDescription = stringResource(R.string.action_addReaction),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * [QuickReactionStrip] in a popup anchored to the bubble this composable sits in, placed the way
 * iOS places it: above the bubble with a 16dp gap (below when there's no room under [minTop]),
 * hugging the sender's side, and never within 16dp of the window's edges.
 */
@Composable
fun QuickReactionStripPopup(
    entries: List<ReactionStrip.Entry>,
    hugsTrailing: Boolean,
    onToggle: (emoji: String) -> Unit,
    onOpenPicker: () -> Unit,
    minTop: Dp,
) {
    val density = LocalDensity.current
    val provider = remember(hugsTrailing, minTop, density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = with(density) {
                QuickReactionStripPlacement.position(
                    anchor = anchorBounds,
                    window = windowSize,
                    strip = popupContentSize,
                    hugsTrailing = hugsTrailing,
                    margin = 16.dp.roundToPx(),
                    gap = 16.dp.roundToPx(),
                    minTop = minTop.roundToPx(),
                )
            }
        }
    }
    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        QuickReactionStrip(entries = entries, onToggle = onToggle, onOpenPicker = onOpenPicker)
    }
}

/** Where [QuickReactionStripPopup] puts the strip, in window pixels. */
object QuickReactionStripPlacement {
    fun position(
        anchor: IntRect,
        window: IntSize,
        strip: IntSize,
        hugsTrailing: Boolean,
        margin: Int,
        gap: Int,
        minTop: Int,
    ): IntOffset {
        val hugged = if (hugsTrailing) {
            minOf(anchor.right, window.width - margin) - strip.width
        } else {
            maxOf(anchor.left, margin)
        }
        val x = hugged.coerceIn(margin, maxOf(margin, window.width - margin - strip.width))
        val above = anchor.top - gap - strip.height
        val y = if (above >= minTop) above else anchor.bottom + gap
        return IntOffset(x, y.coerceIn(minTop, maxOf(minTop, window.height - strip.height)))
    }
}
