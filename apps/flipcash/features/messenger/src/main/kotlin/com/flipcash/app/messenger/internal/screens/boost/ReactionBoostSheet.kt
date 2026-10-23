package com.flipcash.app.messenger.internal.screens.boost

import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.flipcash.shared.chat.ui.boostCentsText
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
 * @param split who the boost pays, which picks the subtitle; the subtitle waits until it is known.
 * @param emoji the viewer's own reaction being boosted, or null before their reactions load.
 * @param canCycle whether the viewer has more than one reaction, so tapping [emoji] moves to the
 * next with [onCycleEmoji].
 * @param customCents an amount entered on the keypad, which the "…" chip shows once chosen.
 */
@Composable
internal fun ReactionBoostSheet(
    emoji: String?,
    split: ReactionBoostViewModel.SplitCopy?,
    canCycle: Boolean,
    token: Token?,
    presetCents: List<Long>,
    selectedCents: Long?,
    customCents: Long?,
    sendProgress: LoadingSuccessState,
    canConfirm: Boolean,
    onCycleEmoji: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
        ) {
            Text(
                text = stringResource(R.string.title_paidReactions),
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textMain,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BoostEmojiPill(
                    emoji = emoji.orEmpty(),
                    canCycle = canCycle,
                    onCycle = onCycleEmoji,
                )
                // The pill's click label already says what a tap does, so this stays out of semantics.
                if (canCycle) {
                    Text(
                        text = stringResource(R.string.label_tapToChange),
                        style = CodeTheme.typography.textSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = CodeTheme.colors.textSecondary,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
            if (split != null) Text(
                text = stringResource(
                    when (split) {
                        ReactionBoostViewModel.SplitCopy.Dm -> R.string.subtitle_paidReactionsDm
                        ReactionBoostViewModel.SplitCopy.Shared -> R.string.subtitle_paidReactionsSplit
                        ReactionBoostViewModel.SplitCopy.Whole -> R.string.subtitle_paidReactionsWhole
                    },
                ),
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
                    Text(text = boostCentsText(cents, wholeDollars = true), style = AmountChipDefaults.TextStyle, color = color)
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
                    text = customCents?.let { boostCentsText(it) } ?: "…",
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

/**
 * The reaction being paid for, drawn as an enlarged selected reaction pill (node 10330:18168):
 * a 10% white capsule with a 38% white outline around a 24pt emoji.
 */
@Composable
private fun BoostEmojiPill(
    emoji: String,
    canCycle: Boolean,
    onCycle: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(color = White10, shape = shape)
            .border(width = 2.8.dp, color = Color.White.copy(alpha = 0.38f), shape = shape)
            .clickable(
                enabled = canCycle,
                onClickLabel = stringResource(R.string.action_switchPaidReaction),
                onClick = onCycle,
            )
            .padding(horizontal = 17.dp, vertical = 8.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = CodeTheme.typography.textMedium.copy(fontSize = 24.sp, lineHeight = 31.sp),
            color = CodeTheme.colors.textMain,
        )
    }
}
