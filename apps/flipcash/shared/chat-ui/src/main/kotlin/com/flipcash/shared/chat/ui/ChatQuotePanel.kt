package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.PriceWithFlag
import com.getcode.ui.core.addIf

/**
 * A citation of another message inside a sent bubble: a rule in the author's colour, their name, and
 * a snippet of what they said, on a ground tinted by that same colour.
 *
 * The composer's citation is [ComposerReplyStrip], not this. They look alike and were one component
 * until the composer half was matched to iOS, which grounds it in glass sampling the transcript
 * while this one is tinted — a panel inside a filled bubble cannot blur what is behind it, because
 * what is behind it is the bubble. Sharing a composable meant either carrying a mode flag or padding
 * one into the other's shape, so they are separate and each keeps its styling in its own defaults.
 */
@Composable
fun ChatQuotePanel(
    quote: ChatQuote,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = quote.accent ?: CodeTheme.colors.tertiary
    val name = quote.nameAccent ?: accent

    Row(
        // Intrinsic minimum, so the rule can fill a height the text column decides. Without it a
        // fillMaxHeight child in an unbounded Row measures to zero.
        modifier = modifier
            .clip(QuotePanelDefaults.shape)
            // The author's own colour at low alpha rather than a neutral scrim: the panel sits on a
            // filled bubble, and tinting it to match the rule is what separates the two surfaces.
            .background(accent.copy(alpha = QuotePanelDefaults.groundAlpha))
            .addIf(onClick != null) { Modifier.clickable { onClick?.invoke() } }
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(QuotePanelDefaults.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Square and flush to the panel's leading edge; the panel's own clip is what rounds it.
        Box(
            modifier = Modifier
                .width(QuotePanelDefaults.accentWidth)
                .fillMaxHeight()
                .background(accent),
        )

        Column(
            modifier = Modifier
                .padding(
                    end = QuotePanelDefaults.trailingPadding,
                    top = QuotePanelDefaults.verticalPadding,
                    bottom = QuotePanelDefaults.verticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(QuotePanelDefaults.nameGap),
        ) {
            Text(
                text = quote.authorName,
                style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (val snippet = quote.snippet) {
                // Two lines, not one: one truncates most quoted sentences mid-clause.
                is ChatQuoteSnippet.Text -> Text(
                    text = snippet.body,
                    style = CodeTheme.typography.caption,
                    // White alphas rather than the theme's secondary, which is a blue-grey: the
                    // ground under this text is the author's colour, and a second hue muddies it.
                    color = Color.White.copy(alpha = QuotePanelDefaults.snippetAlpha),
                    maxLines = QuotePanelDefaults.snippetMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )

                // A quoted payment shows what identifies it, not just a number.
                is ChatQuoteSnippet.Cash -> {
                    val exchange = LocalExchange.current
                    val currencyCode = snippet.amount.currencyCode.name
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(QuotePanelDefaults.cashGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PriceWithFlag(
                            amount = snippet.amount.formatted(),
                            currencyCode = currencyCode,
                            iconSize = QuotePanelDefaults.flagSize,
                            flag = exchange.getFlagByCurrency(currencyCode),
                            text = { formatted ->
                                Text(
                                    text = formatted,
                                    style = CodeTheme.typography.caption,
                                    color = Color.White.copy(
                                        alpha = QuotePanelDefaults.amountAlpha,
                                    ),
                                    maxLines = 1,
                                )
                            },
                        )
                        Text(
                            text = snippet.tokenName,
                            style = CodeTheme.typography.caption,
                            color = Color.White.copy(alpha = QuotePanelDefaults.tokenAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Every measurement the panel makes, carried over from iOS. */
private object QuotePanelDefaults {
    val shape = RoundedCornerShape(8.dp)
    val gap = 8.dp
    val trailingPadding = 8.dp
    val verticalPadding = 6.dp
    val nameGap = 1.dp
    val cashGap = 5.dp
    val accentWidth = 3.dp
    val flagSize = 14.dp
    const val groundAlpha = 0.14f
    const val snippetAlpha = 0.55f
    const val amountAlpha = 0.75f
    const val tokenAlpha = 0.35f
    const val snippetMaxLines = 2
}
