package com.flipcash.shared.transactionhistory

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.app.core.ui.ActivityAmount
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import com.getcode.util.formatLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A single row in the "Recent" activity list on the Wallet screen (Figma 8966:1910).
 *
 * Layout: [avatar slot] · [title + relative time (weight 1)] · [signed amount]
 *
 * @param onClick Opens the entry's details (Figma node 9708:105260). Null leaves the row inert —
 * the wallet's preview and the full history both pass one, but a row can also be drawn purely as a
 * summary.
 */
@Composable
fun ActivityFeedRow(
    item: TransactionListItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .addIf(onClick != null) { Modifier.clickable(onClick = onClick!!) }
            .padding(vertical = CodeTheme.dimens.grid.x2),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransactionAvatarImage(item.avatar)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Text(
                text = item.title,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatActivityTimestamp(item.timestamp),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }

        val fee = item.fee
        val amount = item.amount
        if (fee != null && amount != null) {
            // A convert is two amounts in one row: what left the source token, and what the swap
            // cost. It carries no sign — the pairing, not a prefix, is what reads as an exchange.
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            ) {
                Text(
                    text = amount.nativeAmount.formatted(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.label_activity_convertFee, fee.formatted()),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
        } else {
            amount?.let { fiat ->
                ActivityAmount(
                    amount = fiat,
                    signPrefix = item.signedAmountPrefix?.ifEmpty { null },
                )
            }
        }
    }
}

/**
 * Returns a human-readable relative timestamp, matching the behaviour of
 * `formatLastActivity` in `:shared:chat-ui` (but without depending on that module,
 * which would create a circular dep via chat → tokens → transaction-history).
 */
@Composable
internal fun formatActivityTimestamp(instant: Instant): String {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val tz = TimeZone.currentSystemDefault()
    val todayDate = Clock.System.now().toLocalDateTime(tz).date
    val messageDate = instant.toLocalDateTime(tz).date
    val dayDiff = todayDate.toEpochDays() - messageDate.toEpochDays()
    val time = instant.formatLocalized("h:mm a", is24Hour = is24Hour, if24Hour = "H:mm")
    return when {
        dayDiff == 0L -> time
        dayDiff == 1L -> stringResource(R.string.label_chatReceipt_yesterday)
        dayDiff in 2L..6L -> instant.formatLocalized("EEEE")
        messageDate.year == todayDate.year -> instant.formatLocalized("MMM d")
        else -> instant.formatLocalized("MMM d, yyyy")
    }
}
