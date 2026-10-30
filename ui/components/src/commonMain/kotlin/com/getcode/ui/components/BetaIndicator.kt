package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.resources.Res
import com.getcode.ui.components.resources.subtitle_beta

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
            text = stringResource(Res.string.subtitle_beta),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary
        )
    }
}