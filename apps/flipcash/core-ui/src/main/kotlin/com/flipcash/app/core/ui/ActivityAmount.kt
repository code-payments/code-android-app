package com.flipcash.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.theme.CodeTheme

/**
 * The amount column of an activity/transaction row: the entry in the viewer's own currency, and —
 * only when the payment was denominated in someone else's — what actually moved, flagged, underneath.
 * A tip of 7,500 pesos reads "-$5.00" to a viewer in dollars, with "-$7,500.00" under an Argentine
 * flag below it.
 *
 * Both lines carry [signPrefix], so a debit reads as one whichever line you look at — see [sign]
 * for the amount that signs itself.
 *
 * @param showViewerFlag whether the top line is flagged too. The per-token transaction history flags
 * every amount it shows, so it opts in; the wallet's activity rows leave the viewer's own currency
 * unflagged and let the flag mark the foreign line alone.
 */
@Composable
fun ActivityAmount(
    amount: LocalFiat,
    modifier: Modifier = Modifier,
    signPrefix: String? = null,
    showViewerFlag: Boolean = false,
) {
    val exchange = LocalExchange.current
    // Observed rather than read once: the viewer can change their currency from a screen stacked
    // over the list, and these rows are re-mapped only when a profile or token cache lands, so
    // nothing else would bring the new currency back down here. Cross-rates for a non-USDF entry
    // are a snapshot taken alongside it — see `forViewer`, which only needs them as a fallback.
    val preferredRate by exchange.observePreferredRate().collectAsState(initial = exchange.preferredRate)
    val amounts = amount.forViewer(preferredRate, exchange.rates())

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        if (showViewerFlag) {
            FlagWithFiat(
                fiat = amounts.viewer,
                extraPrefix = amounts.viewer.sign(signPrefix),
                spacing = FLAG_SPACING,
            )
        } else {
            Text(
                text = amounts.viewer.formatted(extraPrefix = amounts.viewer.sign(signPrefix)),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
            )
        }

        amounts.transferred?.let { transferred ->
            FlagWithFiat(
                fiat = transferred,
                extraPrefix = transferred.sign(signPrefix),
                iconSize = CodeTheme.dimens.staticGrid.x2,
                spacing = FLAG_SPACING,
                textStyle = CodeTheme.typography.textSmall,
                textColor = CodeTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * The sign to render ahead of this amount, or null when the value already carries its own. Activity
 * amounts arrive as magnitudes with their direction alongside them, which is why the row supplies a
 * sign at all; a value that is genuinely negative formats its own "-", and prefixing that as well
 * would read "--$5.00".
 */
internal fun Fiat.sign(prefix: String?): String? = prefix?.takeUnless { isNegative }

/**
 * How far the flag sits from the figure it belongs to. Tighter than [FlagWithFiat]'s default, which
 * is set for a lone amount rather than for two of them stacked.
 */
private val FLAG_SPACING: Dp
    @Composable get() = CodeTheme.dimens.grid.x1
