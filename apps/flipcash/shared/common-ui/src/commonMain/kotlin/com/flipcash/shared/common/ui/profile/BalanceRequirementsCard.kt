package com.flipcash.shared.common.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.flipcash.shared.common.ui.resources.Res as CommonUiRes
import com.flipcash.shared.common.ui.resources.label_balanceRequirementChat
import com.flipcash.shared.common.ui.resources.label_balanceRequirementCommunityCurrency
import com.flipcash.shared.common.ui.resources.label_balanceRequirementJoin
import com.flipcash.shared.common.ui.resources.label_balanceRequirementNone
import com.flipcash.shared.common.ui.resources.label_yourBalance
import com.flipcash.shared.common.ui.resources.title_balanceRequirements
import com.getcode.util.resources.Res as UiRes
import com.getcode.util.resources.ic_chevron_right

private val RowHeight = 56.dp
private val CompactRowHeight = 48.dp
private val CardRadius = 12.dp
private val RowInset = 16.dp

/**
 * The balance a group asks for, as a card of two rows, Join and Chat, with the viewer's own
 * balance under it when [yourBalance] is known.
 *
 * Values arrive formatted, since the caller owns currency and token naming: "$10", "$10 of NYC".
 * A null [join] or [chat] reads "None", so a group that only gates speaking still shows a Join row.
 *
 * When [communityCurrency] is set, a Community Currency row leads the card with that content (the
 * token's icon and name) and a chevron, and tapping it calls [onCommunityCurrencyClick]. The caller
 * then passes bare amounts for [join] and [chat], since the token is already named above them.
 *
 * [compact] is the form on Edit Group: a 16 header, shorter rows with their labels in the same 16
 * as the values, and the [footnote] beneath. The profile's own is the default.
 *
 * [onJoinClick] and [onChatClick] make their row tappable and give it a trailing chevron — Edit
 * Group passes them to open the requirement's editor; the read-only profile passes neither.
 */
@Composable
fun BalanceRequirementsCard(
    join: String?,
    chat: String?,
    yourBalance: String?,
    modifier: Modifier = Modifier,
    communityCurrency: (@Composable RowScope.() -> Unit)? = null,
    onCommunityCurrencyClick: () -> Unit = {},
    compact: Boolean = false,
    footnote: String? = null,
    onJoinClick: (() -> Unit)? = null,
    onChatClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
    ) {
        Text(
            text = stringResource(CommonUiRes.string.title_balanceRequirements),
            style = if (compact) CodeTheme.typography.textMedium else CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius))
                .background(White05),
        ) {
            if (communityCurrency != null) {
                CommunityCurrencyRow(onClick = onCommunityCurrencyClick, content = communityCurrency)
                RowSeparator()
            }
            RequirementRow(
                label = stringResource(CommonUiRes.string.label_balanceRequirementJoin),
                value = join ?: stringResource(CommonUiRes.string.label_balanceRequirementNone),
                compact = compact,
                onClick = onJoinClick,
            )
            RowSeparator()
            RequirementRow(
                label = stringResource(CommonUiRes.string.label_balanceRequirementChat),
                value = chat ?: stringResource(CommonUiRes.string.label_balanceRequirementNone),
                compact = compact,
                onClick = onChatClick,
            )
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
        // The balance resolves after the rules; it fades in rather than popping, and keeps its
        // last value while fading out.
        var shownBalance by remember { mutableStateOf(yourBalance) }
        if (yourBalance != null) shownBalance = yourBalance
        AnimatedVisibility(
            visible = yourBalance != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RowInset),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(CommonUiRes.string.label_yourBalance),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
                Text(
                    text = shownBalance.orEmpty(),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RowSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CodeTheme.dimens.border)
            .background(CodeTheme.colors.divider)
    )
}

@Composable
private fun CommunityCurrencyRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = RowInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(CommonUiRes.string.label_balanceRequirementCommunityCurrency),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
        content()
        Icon(
            painter = painterResource(UiRes.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun RequirementRow(label: String, value: String, compact: Boolean, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) CompactRowHeight else RowHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = RowInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (compact) CodeTheme.typography.textMedium else CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            if (onClick != null) {
                Icon(
                    modifier = Modifier.padding(start = 8.dp),
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = CodeTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_BalanceRequirementsCard() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BalanceRequirementsCard(join = "$10 of NYC", chat = "$25 of NYC", yourBalance = "$4.50 of NYC")
        BalanceRequirementsCard(join = null, chat = "$2.50", yourBalance = null)
        BalanceRequirementsCard(
            join = "$1",
            chat = "$1",
            yourBalance = "$4.50 of Moony",
            communityCurrency = { Text(text = "Moony", style = CodeTheme.typography.textMedium, color = CodeTheme.colors.textMain) },
        )
        BalanceRequirementsCard(
            join = "$10",
            chat = null,
            yourBalance = null,
            compact = true,
            onJoinClick = {},
            onChatClick = {},
        )
    }
}
