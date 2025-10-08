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
import com.flipcash.app.balance.internal.BalanceViewModel
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.ui.FlagWithFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import com.getcode.ui.core.noRippleClickable
import com.getcode.util.DateUtils
import com.getcode.util.format
import kotlinx.datetime.Instant


@Composable
internal fun FeedItemSummary(
    message: ActivityFeedMessage,
    canViewDetails: Boolean,
    modifier: Modifier = Modifier,
    onViewDetails: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = modifier
            .addIf(canViewDetails) {
                Modifier.noRippleClickable(
                    enabled = message.isTransaction,
                    onClick = onViewDetails
                )
            }
            .addIf(!canViewDetails) {
                Modifier.clickable(
                    enabled = message.canCancel,
                    onClick = onCancel
                )
            }
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
                FlagWithFiat(fiat = amount.nativeAmount)
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