package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * The quoted original above the composer while a reply is being written: a rule in the author's own
 * colour flush against the screen's leading edge, their name over one or two lines of what they
 * said, and the way out on the trailing edge.
 *
 * Deliberately not [ChatQuotePanel]. There is no card and no tinted ground here, because the strip
 * is not a thing sitting on the bar — it *is* the top of the bar, and the rule running the full
 * height of the region the bar grew by is what makes the reveal read as the bar getting taller
 * rather than as a panel arriving. The panel inside a sent bubble has the opposite problem: it sits
 * on a filled bubble and needs an edge to separate it, so the two surfaces stay separate composables.
 *
 * Matches iOS's `ComposerReplyStrip` measurement for measurement, including the rule at x = 0 with
 * no leading inset of any kind.
 */
@Composable
fun ComposerReplyStrip(
    quote: ChatQuote,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rule = quote.accent ?: CodeTheme.colors.tertiary
    val name = quote.nameAccent ?: rule

    Row(
        // Intrinsic minimum, so the rule can fill a height the text column decides. Without it a
        // fillMaxHeight child in an unbounded Row measures to zero.
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(end = ComposerReplyStripDefaults.trailingPadding),
        horizontalArrangement = Arrangement.spacedBy(ComposerReplyStripDefaults.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Square, unclipped, and with no leading padding on the row: it starts at the screen edge.
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

        // A large, thin ✕ rather than a small bold one, sized to sit against two lines of quote
        // without crowding them, in a hit target wider than the glyph.
        Box(
            modifier = Modifier
                .size(ComposerReplyStripDefaults.dismissTarget)
                .clickable(onClick = onDismiss)
                .testTag("action_cancel_reply"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.requiredSize(ComposerReplyStripDefaults.dismissGlyph),
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.action_cancelReply),
                tint = CodeTheme.colors.textSecondary,
            )
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
    val ruleWidth = 4.dp
    val gap = 9.dp
    val nameGap = 2.dp
    val cashGap = 6.dp
    val textPadding = 8.dp
    val trailingPadding = 8.dp

    /** Sized to the cap height of the amount beside it, so the flag reads as a mark on the line. */
    val flagSize = 16.dp
    val dismissTarget = 34.dp
    val dismissGlyph = 20.dp
    const val textMaxLines = 2
}
