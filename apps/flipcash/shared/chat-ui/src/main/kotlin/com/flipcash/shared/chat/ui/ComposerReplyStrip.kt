package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.core.R
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.PriceWithFlag
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * The quoted original above the composer while a reply is being written: a card carrying a rule in
 * the author's own colour, their name over one or two lines of what they said, and the way out on
 * the trailing edge.
 *
 * The card is glass rather than a flat fill, matching iOS, so it reads as a surface floating over
 * the transcript rather than as part of the bar. That is also why the dismiss control is a filled
 * disc: the ground behind it samples whatever message is scrolled underneath, and a hairline glyph
 * on its own changed contrast as the transcript moved.
 *
 * Deliberately not [ChatQuotePanel], which sits inside a filled bubble and is tinted by the author's
 * colour rather than blurred.
 */
@Composable
fun ComposerReplyStrip(
    quote: ChatQuote,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val rule = quote.accent ?: CodeTheme.colors.tertiary
    val name = quote.nameAccent ?: rule

    val shape = RoundedCornerShape(ComposerReplyStripDefaults.cornerRadius)
    // Same liquid glass as the nav pill and the app bar's circular buttons: a wide blur plus a tint
    // toward a grey lifted off the (near-black) background, so the card reads as light frosted glass
    // above dark content rather than as the background tone. `clip` must precede `hazeBlur` to bound
    // the blur to the rounded rect. Falls back to a near-opaque fill of the same tint when the host
    // has no HazeState to sample.
    val backdrop = CodeTheme.colors.background
    val glassBlurRadius = CodeTheme.dimens.grid.x4
    val glassTint = lerp(backdrop, Color.White, 0.18f)
    // The HazeBlurStyle builder is not a @Composable scope, so theme reads are hoisted above it.
    val liquidGlass = HazeBlurStyle {
        blurRadius(glassBlurRadius)
        backgroundColor(backdrop)
        colorEffects(listOf(HazeColorEffect.tint(glassTint.copy(alpha = 0.72f))))
    }
    val ground = if (hazeState != null) {
        Modifier.hazeBlur(HazeInput.Sources(hazeState), liquidGlass)
    } else {
        Modifier.background(glassTint.copy(alpha = 0.9f))
    }

    Row(
        // heightIn before the intrinsic pass: `height(IntrinsicSize.Min)` enforces the incoming
        // constraints, so the floor survives it. Intrinsic minimum, in turn, is what lets the rule
        // fill a height the text column decides — a fillMaxHeight child in an unbounded Row measures
        // to zero.
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(ground)
            .border(CodeTheme.dimens.border, Color.White.copy(alpha = 0.08f), shape)
            .heightIn(min = ComposerReplyStripDefaults.minHeight)
            .height(IntrinsicSize.Min)
            .padding(end = ComposerReplyStripDefaults.trailingPadding),
        horizontalArrangement = Arrangement.spacedBy(ComposerReplyStripDefaults.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Square and flush to the card's leading edge; the card's own clip is what rounds it.
        Box(
            modifier = Modifier
                .width(ComposerReplyStripDefaults.ruleWidth)
                .fillMaxHeight()
                .background(rule),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = ComposerReplyStripDefaults.textPadding),
            verticalArrangement = Arrangement.spacedBy(ComposerReplyStripDefaults.nameGap),
        ) {
            Text(
                text = quote.authorName,
                style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            QuoteLine(quote.snippet)
        }

        // A disc rather than a bare ✕, in a hit target wider than the disc.
        Box(
            modifier = Modifier
                .size(ComposerReplyStripDefaults.dismissTarget)
                .clickable(onClick = onDismiss)
                .testTag("action_cancel_reply"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(ComposerReplyStripDefaults.dismissDisc)
                    .background(
                        CodeTheme.colors.textSecondary.copy(alpha = 0.35f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.requiredSize(ComposerReplyStripDefaults.dismissGlyph),
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_cancelReply),
                    tint = CodeTheme.colors.textMain,
                )
            }
        }
    }
}

/**
 * The quoted original itself, one step under the bubble body's size and in the same weight: the
 * quote is the subject of the strip, so it is read rather than glanced at. Primary text for the same
 * reason — dimming it makes it look like placeholder text for the field below.
 */
@Composable
private fun QuoteLine(snippet: ChatQuoteSnippet) {
    val style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.Medium)
    when (snippet) {
        is ChatQuoteSnippet.Text -> Text(
            text = snippet.body,
            style = style,
            color = CodeTheme.colors.textMain,
            maxLines = ComposerReplyStripDefaults.textMaxLines,
            overflow = TextOverflow.Ellipsis,
        )

        // A payment carries the flag and the mint's name the cash card leads with. The amount alone
        // reads as a number; with the flag beside it, it reads as the payment being answered.
        is ChatQuoteSnippet.Cash -> {
            val exchange = LocalExchange.current
            val currencyCode = snippet.amount.currencyCode.name
            Row(
                horizontalArrangement = Arrangement.spacedBy(ComposerReplyStripDefaults.cashGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PriceWithFlag(
                    amount = snippet.amount.formatted(),
                    currencyCode = currencyCode,
                    iconSize = ComposerReplyStripDefaults.flagSize,
                    flag = exchange.getFlagByCurrency(currencyCode),
                    text = { formatted ->
                        Text(
                            text = formatted,
                            style = style,
                            color = CodeTheme.colors.textMain,
                            maxLines = 1,
                        )
                    },
                )
                Text(
                    text = snippet.tokenName,
                    style = style,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Every measurement the strip makes, carried over from iOS. */
private object ComposerReplyStripDefaults {
    val cornerRadius = 14.dp
    val ruleWidth = 6.dp
    val gap = 9.dp
    val nameGap = 2.dp
    val cashGap = 6.dp
    val textPadding = 8.dp
    val trailingPadding = 8.dp

    /** The composer field's own height, so a one-line quote does not sit shorter than it. */
    val minHeight = 50.dp

    /** Sized to the cap height of the amount beside it, so the flag reads as a mark on the line. */
    val flagSize = 16.dp
    val dismissTarget = 34.dp
    val dismissDisc = 22.dp
    val dismissGlyph = 11.dp
    const val textMaxLines = 2
}
