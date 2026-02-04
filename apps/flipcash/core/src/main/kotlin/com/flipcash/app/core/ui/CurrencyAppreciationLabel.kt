package com.flipcash.app.core.ui

import androidx.compose.foundation.background
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
import com.flipcash.core.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall

@Composable
fun CurrencyAppreciationLabel(
    appreciation: Fiat,
    modifier: Modifier = Modifier,
) {
    val hasAppreciation = appreciation.decimalValue >= 0
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
        Text(
            modifier = Modifier
                .background(
                    color = changeColor.copy(0.20f),
                    shape = MaterialTheme.shapes.extraSmall,
                )
                .padding(
                    vertical = 2.dp,
                    horizontal = CodeTheme.dimens.grid.x1
                ),
            text = appreciation.formatted(
                extraPrefix = if (hasAppreciation) "+" else "-",
            ),
            style = CodeTheme.typography.textSmall,
            color = changeColor,
        )

        val label = if (hasAppreciation) {
            stringResource(R.string.label_fromCurrencyAppreciation)
        } else {
            stringResource(R.string.label_fromCurrencyDepreciation)
        }
        Text(
            text = label,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}