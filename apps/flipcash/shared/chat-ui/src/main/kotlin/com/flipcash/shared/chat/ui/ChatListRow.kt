package com.flipcash.shared.chat.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Badge
import com.getcode.util.formatLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun ChatListRow(
    avatar: @Composable () -> Unit,
    title: @Composable RowScope.() -> Unit,
    subtitle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    endAction: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CodeTheme.colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("send_contact_row")
                .clickable(onClick = onClick)
                .padding(vertical = CodeTheme.dimens.inset)
                .padding(end = CodeTheme.dimens.inset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Spacer used by the unread indicator that we moved
                // left for spacing compatibility and easy add back later
                Box(modifier = Modifier.requiredWidth(CodeTheme.dimens.inset))
                avatar()
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                ) {
                    title()
                }

                subtitle()
            }

            endAction()
        }
        if (showDivider) {
            HorizontalDivider(
                color = CodeTheme.colors.divider,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(
                        start = CodeTheme.dimens.inset + CodeTheme.dimens.staticGrid.x8 + CodeTheme.dimens.grid.x3,
                        end = CodeTheme.dimens.inset,
                    ),
            )
        }
    }
}

/**
 * The chat's unread count in a pill beside the timestamp (node 10329:8245): dark text on the
 * indicator colour, 18dp tall, a circle for one digit and widening for more.
 */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Badge(
        modifier = modifier,
        count = count,
        color = CodeTheme.colors.indicator,
        contentColor = CodeTheme.colors.background,
        // The default caption line height (18sp) plus the badge's vertical padding would make the
        // pill 24dp; the design holds it to 18dp with the digits tracked in slightly.
        textStyle = CodeTheme.typography.caption.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 12.sp,
            letterSpacing = (-0.06).em,
        ),
    )
}

@Composable
fun formatLastActivity(instant: Instant): String {
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