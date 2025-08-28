package com.flipcash.app.onramp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.flipcash.shared.onramp.common.R

@Composable
fun AddCashRow(
    modifier: Modifier = Modifier,
    onAddCash: () -> Unit,
    onWithdraw: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodeButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.action_addCash),
            buttonState = ButtonState.Filled,
            onClick = onAddCash
        )

        CodeButton(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.action_withdraw),
            buttonState = ButtonState.Filled20,
            onClick = onWithdraw
        )
    }
}