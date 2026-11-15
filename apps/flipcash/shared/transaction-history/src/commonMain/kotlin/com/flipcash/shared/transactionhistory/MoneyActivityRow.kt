package com.flipcash.shared.transactionhistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.core.resources.Res as CoreRes
import com.flipcash.core.resources.label_chatReceipt_yesterday
import com.flipcash.core.resources.label_chatSeparator_today
import com.flipcash.core.resources.title_moneyNoActivity
import com.flipcash.core.resources.title_moneyRecentActivity
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import com.getcode.util.formatLocalized
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The Money tab's Recent Activity (node 13729:744): today's tappable header under the Money title,
 * then a [MoneyActivityRow] per entry.
 *
 * One block rather than a list item per row, so the tab can stretch it over the height left under
 * its buttons: given a minimum height it fills it, and with more rows than that it grows past it.
 * The preview is bounded upstream, so the rows are never many.
 *
 * @param showEmpty Whether no rows means no activity. False while the feed is still a cache that
 *   has not heard from the server, so an established account is not told it has none.
 * @param headerModifier The header row's padding and click target, as [recentActivitySection]'s
 *   `modifier` is.
 * @param onItemClick Opens one entry's details; null leaves the rows inert.
 */
@Composable
fun MoneyActivitySection(
    transactions: List<TransactionListItem>,
    showEmpty: Boolean,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    onItemClick: ((TransactionListItem) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        RecentActivityHeader(title = CoreRes.string.title_moneyRecentActivity, modifier = headerModifier)
        if (transactions.isEmpty()) {
            if (showEmpty) {
                // Centred in whatever height the section was given below its header; at its own
                // height when there is none to spare.
                Spacer(Modifier.weight(1f))
                MoneyActivityEmpty()
                Spacer(Modifier.weight(1f))
            }
        } else {
            transactions.forEach { item ->
                key(item.id) {
                    MoneyActivityRow(
                        item = item,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onItemClick?.let { click -> { click(item) } },
                    )
                }
            }
        }
    }
}

/**
 * No activity yet, in one line as on iOS. The full history keeps its own two lines ("No Activity"
 * and what will appear there), in the same type as this.
 */
@Composable
private fun MoneyActivityEmpty() {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CodeTheme.dimens.grid.x4),
        text = stringResource(CoreRes.string.title_moneyNoActivity),
        style = CodeTheme.typography.textLarge,
        color = CodeTheme.colors.textMain,
        textAlign = TextAlign.Center,
    )
}

/** Which day an activity entry happened on, relative to the viewer's today. */
sealed interface ActivityDay {
    data object Today : ActivityDay
    data object Yesterday : ActivityDay
    data class OnDate(val date: LocalDate) : ActivityDay
}

/**
 * The day [timestamp] fell on as seen in [timeZone], relative to [now]. A timestamp ahead of [now]
 * (a server clock running fast) still reads as today.
 */
fun activityDay(timestamp: Instant, now: Instant, timeZone: TimeZone): ActivityDay {
    val today = now.toLocalDateTime(timeZone).date
    val day = timestamp.toLocalDateTime(timeZone).date
    return when (today.toEpochDays() - day.toEpochDays()) {
        in Long.MIN_VALUE..0L -> ActivityDay.Today
        1L -> ActivityDay.Yesterday
        else -> ActivityDay.OnDate(day)
    }
}

/**
 * The line under a Money activity row's title: who and when, "Ella · Today" (node 13729:744). With
 * no name it is the day alone, never a dangling separator.
 */
fun moneyActivitySubtitle(counterpartyName: String?, day: String): String =
    counterpartyName?.takeIf { it.isNotBlank() }?.let { "$it · $day" } ?: day

/**
 * One row of the Money tab's Recent Activity (node 13729:744): the group's name when the entry
 * happened in a group the app can name, who and when underneath, and the signed amount.
 *
 * An entry without a group name keeps today's title and avatar, and its line underneath is the day
 * alone, since that title already names the counterparty.
 *
 * @param now The moment "Today" is measured from. A parameter so previews and tests can fix it.
 * @param onClick Opens the entry's details. Null leaves the row inert.
 */
@Composable
fun MoneyActivityRow(
    item: TransactionListItem,
    modifier: Modifier = Modifier,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    onClick: (() -> Unit)? = null,
) {
    val groupName = item.chatName
    val day = activityDay(item.timestamp, now, timeZone).label(item.timestamp, now, timeZone)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .addIf(onClick != null) { Modifier.clickable(onClick = onClick!!) }
            .padding(vertical = CodeTheme.dimens.grid.x2),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoneyActivityAvatar(item = item, groupName = groupName)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Text(
                text = groupName ?: item.title,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = moneyActivitySubtitle(
                    counterpartyName = item.counterpartyName.takeIf { groupName != null },
                    day = day,
                ),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ActivityRowAmount(item)
    }
}

/**
 * The day as a row reads it: "Today", "Yesterday", then the weekday for the rest of the week and
 * the date after that, the same ladder [formatActivityTimestamp] uses past today.
 */
@Composable
private fun ActivityDay.label(timestamp: Instant, now: Instant, timeZone: TimeZone): String = when (this) {
    ActivityDay.Today -> stringResource(CoreRes.string.label_chatSeparator_today)
    ActivityDay.Yesterday -> stringResource(CoreRes.string.label_chatReceipt_yesterday)
    is ActivityDay.OnDate -> {
        val today = now.toLocalDateTime(timeZone).date
        when {
            today.toEpochDays() - date.toEpochDays() in 2L..6L -> timestamp.formatLocalized("EEEE")
            date.year == today.year -> timestamp.formatLocalized("MMM d")
            else -> timestamp.formatLocalized("MMM d, yyyy")
        }
    }
}

/**
 * The row's avatar: the group's picture (its initials without one) on a row titled by the group,
 * in the same slot [TransactionAvatarImage] fills; today's avatar on every other row.
 */
@Composable
private fun MoneyActivityAvatar(item: TransactionListItem, groupName: String?) {
    val chatId = item.chatId
    if (groupName == null || chatId == null) {
        TransactionAvatarImage(item.avatar)
        return
    }
    Box(
        modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x10),
        contentAlignment = Alignment.Center,
    ) {
        ContactAvatar(
            image = item.chatPicture,
            displayName = groupName,
            // A group's picture hangs off the chat's profile, not a member's.
            access = BlobAccessContext.ChatProfile(chatId),
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x8)
                .clip(CircleShape),
        )
    }
}
