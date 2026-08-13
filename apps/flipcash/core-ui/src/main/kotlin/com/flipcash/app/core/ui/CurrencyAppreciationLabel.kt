package com.flipcash.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.money.formattedAppreciation
import com.flipcash.core.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.White20
import com.getcode.theme.extraSmall
import com.getcode.ui.components.text.AnimatedNumberText

/** Controls the visual treatment of the appreciation badge. */
enum class AppreciationStyle {
    /** Sentiment-tinted filled pill (green/red). Used in v1 balance and token-info screens. */
    Classic,
    /**
     * Neutral bordered pill with secondary text colour. Used in the v2 wallet screen.
     * Layout: `[signed delta in a bordered box]  [label]`, both in secondary text.
     */
    Pill,
}

/**
 * Displays a fiat appreciation/depreciation value with an optional label.
 *
 * [style] defaults to [AppreciationStyle.Classic] so existing call sites are unchanged.
 * Pass [AppreciationStyle.Pill] in the v2 wallet header for the neutral bordered treatment.
 */
@Composable
fun CurrencyAppreciationLabel(
    appreciation: Fiat,
    modifier: Modifier = Modifier,
    style: AppreciationStyle = AppreciationStyle.Classic,
) {
    val hasAppreciation = appreciation.toDouble() >= 0
    val changeColor = if (hasAppreciation) {
        CodeTheme.colors.successText
    } else {
        CodeTheme.colors.errorText
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        val pillModifier = when (style) {
            AppreciationStyle.Pill ->
                Modifier
                    .border(
                        width = CodeTheme.dimens.border,
                        color = White20,
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    .padding(
                        vertical = 3.dp,
                        horizontal = CodeTheme.dimens.grid.x1,
                    )
            AppreciationStyle.Classic ->
                Modifier
                    .background(
                        color = changeColor.copy(0.20f),
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    .padding(
                        vertical = 2.dp,
                        horizontal = CodeTheme.dimens.grid.x1,
                    )
        }
        AnimatedNumberText(
            modifier = pillModifier,
            value = appreciation.formattedAppreciation(),
            style = CodeTheme.typography.textSmall,
            color = if (style == AppreciationStyle.Pill) CodeTheme.colors.textSecondary else changeColor,
        )

        val label = when (style) {
            AppreciationStyle.Pill ->
                if (hasAppreciation) {
                    stringResource(R.string.label_fromAppreciation)
                } else {
                    stringResource(R.string.label_fromDepreciation)
                }
            AppreciationStyle.Classic ->
                if (hasAppreciation) {
                    stringResource(R.string.label_fromCurrencyAppreciation)
                } else {
                    stringResource(R.string.label_fromCurrencyDepreciation)
                }
        }
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}
