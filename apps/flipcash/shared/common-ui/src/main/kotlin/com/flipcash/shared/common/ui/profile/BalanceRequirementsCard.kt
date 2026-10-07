package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.shared.common.ui.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05

private val RowHeight = 56.dp
private val CardRadius = 12.dp
private val RowInset = 16.dp

/**
 * The balance a group asks for, as a card of two rows, Join and Chat, with the viewer's own
 * balance under it when [yourBalance] is known.
 *
 * Values arrive formatted, since the caller owns currency and token naming: "$10", "$10 of NYC".
 * A null [join] or [chat] reads "None", so a group that only gates speaking still shows a Join row.
 */
@Composable
fun BalanceRequirementsCard(
    join: String?,
    chat: String?,
    yourBalance: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.title_balanceRequirements),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius))
                .background(White05),
        ) {
            RequirementRow(
                label = stringResource(R.string.label_balanceRequirementJoin),
                value = join ?: stringResource(R.string.label_balanceRequirementNone),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CodeTheme.dimens.border)
                    .background(CodeTheme.colors.divider)
            )
            RequirementRow(
                label = stringResource(R.string.label_balanceRequirementChat),
                value = chat ?: stringResource(R.string.label_balanceRequirementNone),
            )
        }
        if (yourBalance != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RowInset),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.label_yourBalance),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
                Text(
                    text = yourBalance,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RequirementRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = RowInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_BalanceRequirementsCard() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        BalanceRequirementsCard(join = "$10 of NYC", chat = "$25 of NYC", yourBalance = "$4.50 of NYC")
        BalanceRequirementsCard(join = null, chat = "$2.50", yourBalance = null)
    }
}
