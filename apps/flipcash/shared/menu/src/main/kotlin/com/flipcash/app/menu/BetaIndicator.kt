package com.flipcash.app.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.shared.menu.R
import com.getcode.theme.CodeTheme

@Composable
fun BetaIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
    ) {
        Box(
            modifier = Modifier
                .size(CodeTheme.dimens.grid.x1)
                .background(
                    color = CodeTheme.colors.betaIndicator,
                    shape = CircleShape
                )
        )

        Text(
            text = stringResource(R.string.subtitle_beta),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary
        )
    }
}