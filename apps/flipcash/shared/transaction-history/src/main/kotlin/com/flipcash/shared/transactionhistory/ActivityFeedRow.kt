package com.flipcash.shared.transactionhistory

import android.text.format.DateFormat
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.getcode.opencode.model.financial.Token
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.services.models.UserProfile
import com.getcode.theme.CodeTheme
import com.getcode.util.formatLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Figma 9717:14138 — the avatar, the slot that leaves room for the badge's overhang, the badge. */
private val AvatarSize = 40.dp
private val AvatarSlotSize = 48.dp
private val TokenBadgeSize = 20.dp

/**
 * A single row in the "Recent" activity list on the Wallet screen (Figma 8966:1910).
 *
 * Layout: [48dp avatar slot] · [title + relative time (weight 1)] · [signed amount]
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
        AvatarSlot(item.avatar)

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
                    text = amount.formatted(),
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
                Text(
                    text = fiat.formatted(extraPrefix = item.signedAmountPrefix?.ifEmpty { null }),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                )
            }
        }
    }
}

/**
 * The leading slot: the avatar centred in a box wide enough for the token badge to overhang its
 * bottom-right corner without pushing the title (Figma 9717:14138 — 40dp avatar in a 48dp slot,
 * 20dp badge). Every row reserves the full slot, badge or not, so titles line up down the list.
 */
@Composable
private fun AvatarSlot(avatar: TransactionAvatar, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.requiredSize(AvatarSlotSize),
        contentAlignment = Alignment.Center,
    ) {
        val avatarModifier = Modifier
            .requiredSize(AvatarSize)
            .clip(CircleShape)

        when (avatar) {
            is TransactionAvatar.Profile ->
                ContactAvatar(userProfile = avatar.profile, modifier = avatarModifier)
            is TransactionAvatar.TokenIcon ->
                TokenIcon(token = avatar.token, modifier = avatarModifier)
            is TransactionAvatar.SwapTokens ->
                SwapAvatar(avatar, modifier = Modifier.requiredSize(AvatarSize))
            is TransactionAvatar.Generic ->
                ContactAvatar(userProfile = UserProfile.Empty, modifier = avatarModifier)
        }

        // Ringed in the page background so the coin reads as sitting over the avatar rather than
        // being part of it — the same treatment [SwapAvatar] gives its overlapping pair.
        avatar.badgeToken?.let { token ->
            TokenIcon(
                token = token,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .requiredSize(TokenBadgeSize)
                    .border(CodeTheme.dimens.thickBorder, CodeTheme.colors.background, CircleShape),
            )
        }
    }
}

/**
 * A convert's two tokens as overlapping coins — the destination sits over the source, ringed in the
 * page background so the overlap reads as depth. Mirrors iOS's `swapAvatar` in `ActivityRow.swift`.
 */
@Composable
private fun SwapAvatar(
    avatar: TransactionAvatar.SwapTokens,
    modifier: Modifier = Modifier,
) {
    val coin = CodeTheme.dimens.staticGrid.x5
    Box(modifier = modifier) {
        TokenCoin(
            token = avatar.from,
            modifier = Modifier
                .align(Alignment.TopStart)
                .requiredSize(coin),
        )
        TokenCoin(
            token = avatar.to,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .requiredSize(coin)
                .border(CodeTheme.dimens.thickBorder, CodeTheme.colors.background, CircleShape),
        )
    }
}

/** One coin of a [SwapAvatar]; an unresolved side draws the shared placeholder. */
@Composable
private fun TokenCoin(token: Token?, modifier: Modifier) {
    val shaped = modifier.clip(CircleShape)
    if (token != null) {
        TokenIcon(token = token, modifier = shaped)
    } else {
        TokenIcon(image = null, modifier = shaped)
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
