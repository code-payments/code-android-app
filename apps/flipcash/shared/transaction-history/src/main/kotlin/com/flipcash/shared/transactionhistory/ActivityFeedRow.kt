package com.flipcash.shared.transactionhistory

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.TokenIcon
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.services.models.UserProfile
import com.getcode.theme.CodeTheme
import com.getcode.util.formatLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A single row in the "Recent" activity list on the Wallet screen (Figma 8966:1910).
 *
 * Layout: [40dp avatar] · [title + relative time (weight 1)] · [signed amount]
 */
@Composable
fun ActivityFeedRow(
    item: TransactionListItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CodeTheme.dimens.grid.x2),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarModifier = Modifier
            .requiredSize(CodeTheme.dimens.staticGrid.x8)
            .clip(CircleShape)

         when (val a = item.avatar) {
             is TransactionAvatar.Profile ->
                 ContactAvatar(userProfile = a.profile, modifier = avatarModifier)
             is TransactionAvatar.TokenIcon ->
                 TokenIcon(token = a.token, modifier = avatarModifier)
             TransactionAvatar.Generic ->
                 ContactAvatar(userProfile = UserProfile.Empty, modifier = avatarModifier)
         }

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

        item.amount?.let { fiat ->
            Text(
                text = fiat.formatted(extraPrefix = item.signedAmountPrefix?.ifEmpty { null }),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
        }
    }
}

/**
 * Returns a human-readable relative timestamp, matching the behaviour of
 * `formatLastActivity` in `:shared:chat-ui` (but without depending on that module,
 * which would create a circular dep via chat → tokens → transaction-history).
 */
@Composable
private fun formatActivityTimestamp(instant: Instant): String {
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
