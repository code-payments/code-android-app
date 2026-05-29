package com.flipcash.app.transactions.internal.components

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.ui.FlagWithFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import com.getcode.ui.core.noRippleClickable
import com.getcode.util.format
import com.getcode.util.formatLocalized
import kotlin.time.Instant


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

@Composable
private fun Instant.formatRelativeToToday(): String {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    if (DateUtils.isToday(toEpochMilliseconds())) {
        return formatLocalized("hh:mm a", is24Hour = is24Hour, if24Hour = "hh:mm")
    }
    return format("MMMM dd, yyyy")
}