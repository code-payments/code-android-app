package com.flipcash.app.messenger.internal.screens.boost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flipcash.features.messenger.R
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.shared.chat.reactions.formatBoostCents
import com.flipcash.shared.common.ui.amount.AmountChip
import com.flipcash.shared.common.ui.amount.AmountChipDefaults
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.SlideToConfirm
import com.getcode.view.LoadingSuccessState

/**
 * The Paid Reactions sheet (node 10330:18091; an amount picked, node 10330:18196): the emoji being
 * paid for, how the payment is split, which balance pays, the amount, and the swipe.
 *
 * The amounts are US dollars whatever the viewer's preferred currency: the boost is a USD figure
 * on the wire and on the pill.
 *
 * @param customCents an amount entered on the keypad, which the "…" chip shows once chosen.
 */
@Composable
internal fun ReactionBoostSheet(
    emoji: String,
    token: Token?,
    presetCents: List<Long>,
    selectedCents: Long?,
    customCents: Long?,
    sendProgress: LoadingSuccessState,
    canConfirm: Boolean,
    onSelectToken: () -> Unit,
    onSelectAmount: (Long) -> Unit,
    onCustomAmount: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = CodeTheme.dimens.grid.x6, bottom = CodeTheme.dimens.grid.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Text(text = emoji, style = CodeTheme.typography.displayLarge)
            Text(
                text = stringResource(R.string.title_paidReactions),
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = stringResource(R.string.subtitle_paidReactionsSplit),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Text(
                text = stringResource(R.string.label_payWith),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary,
            )
            TokenSelectionPill(
                token = token,
                background = White10,
                textStyle = CodeTheme.typography.textMedium,
                imageSize = CodeTheme.dimens.staticGrid.x3,
                contentPadding = PaddingValues(
                    horizontal = CodeTheme.dimens.grid.x1 + 1.dp,
                    vertical = CodeTheme.dimens.grid.x1 - 1.dp,
                ),
                onClick = onSelectToken,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            presetCents.forEach { cents ->
                AmountChip(
                    selected = selectedCents == cents && customCents != cents,
                    onClick = { onSelectAmount(cents) },
                    modifier = Modifier.weight(1f),
                ) { color ->
                    Text(text = formatWholeDollars(cents), style = AmountChipDefaults.TextStyle, color = color)
                }
            }
            val customLabel = stringResource(R.string.action_customAmount)
            AmountChip(
                selected = customCents != null && selectedCents == customCents,
                onClick = onCustomAmount,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = customLabel },
            ) { color ->
                Text(
                    text = customCents?.let(::formatBoostCents) ?: "…",
                    style = AmountChipDefaults.TextStyle,
                    color = color,
                    maxLines = 1,
                )
            }
        }

        SlideToConfirm(
            onConfirm = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = canConfirm,
            isLoading = sendProgress.loading,
            isSuccess = sendProgress.success,
            label = stringResource(R.string.action_swipeToPay),
        )
    }
}

/** A preset chip's face: whole dollars, as the design draws them ("$5"). */
private fun formatWholeDollars(cents: Long): String =
    if (cents % 100 == 0L) "$${cents / 100}" else formatBoostCents(cents)
