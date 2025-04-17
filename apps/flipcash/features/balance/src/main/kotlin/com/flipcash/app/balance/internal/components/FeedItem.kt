package com.flipcash.app.balance.internal.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.flipcash.app.core.ui.FlagWithFiat
import com.flipcash.services.models.ActivityFeedMessage
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.theme.CodeTheme
import com.getcode.util.DateUtils
import com.getcode.util.format
import kotlinx.datetime.Instant

@Composable
internal fun FeedItem(
    message: ActivityFeedMessage,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(enabled = false) { } // enable for cancellable items
            .padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.inset,
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = message.text,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain
            )
            Text(
                text = message.timestamp.formatRelativeToToday(),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary
            )
        }

        message.amount?.let { amount ->
            Column(
                horizontalAlignment = Alignment.End
            ) {
                FlagWithFiat(fiat = amount.converted)
                if (amount.converted.currencyCode != CurrencyCode.USD) {
                    Text(
                        text = amount.usdc.formatted(suffix = "USDC"),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

private fun Instant.formatRelativeToToday(): String {
    if (DateUtils.isToday(toEpochMilliseconds())) {
        return format("hh:mm a")
    }
    return format("MMMM dd, yyyy")
}