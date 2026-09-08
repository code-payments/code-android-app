package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.PriceWithFlag
import com.getcode.ui.core.addIf

/**
 * A citation of another message: an accent bar, the author's name, and a snippet of what they said.
 *
 * The same composable serves the composer strip and the panel inside a sent bubble, so the two can
 * never drift. Every styling decision lives here and in [QuotePanelDefaults] — the iOS reply UI is
 * still pending design review, and this keeps that review a one-file change.
 */
@Composable
fun ChatQuotePanel(
    quote: ChatQuote,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = quote.accent ?: CodeTheme.colors.tertiary

    Row(
        modifier = modifier
            .clip(QuotePanelDefaults.shape)
            // A white scrim rather than the theme's own ground: the panel sits inside a bubble
            // that is already drawn on that ground, so it would otherwise be invisible.
            .background(White10)
            .addIf(onClick != null) { Modifier.clickable { onClick?.invoke() } }
            .padding(QuotePanelDefaults.padding),
        horizontalArrangement = Arrangement.spacedBy(QuotePanelDefaults.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(QuotePanelDefaults.accentWidth)
                .height(QuotePanelDefaults.accentHeight)
                .clip(RoundedCornerShape(QuotePanelDefaults.accentWidth / 2))
                .background(accent),
        )

        Column(verticalArrangement = Arrangement.spacedBy(QuotePanelDefaults.nameGap)) {
            Text(
                text = quote.authorName,
                style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (val snippet = quote.snippet) {
                // Two lines, not one: one truncates most quoted sentences mid-clause.
                is ChatQuoteSnippet.Text -> Text(
                    text = snippet.body,
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = QuotePanelDefaults.snippetMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )

                // A quoted payment shows what identifies it, not just a number.
                is ChatQuoteSnippet.Cash -> {
                    val exchange = LocalExchange.current
                    val currencyCode = snippet.amount.currencyCode.name
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(QuotePanelDefaults.nameGap),
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
                                    color = CodeTheme.colors.textSecondary,
                                    maxLines = 1,
                                )
                            },
                        )
                        Text(
                            text = snippet.tokenName,
                            style = CodeTheme.typography.caption,
                            color = CodeTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Every visual decision the panel makes, in one place for the pending design review. */
private object QuotePanelDefaults {
    val shape = RoundedCornerShape(8.dp)
    val padding = 8.dp
    val gap = 8.dp
    val nameGap = 2.dp
    val accentWidth = 3.dp
    val accentHeight = 32.dp
    val flagSize = 14.dp
    const val snippetMaxLines = 2
}
