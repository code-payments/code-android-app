package com.flipcash.app.core.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.subtitle_addMoney
import com.flipcash.core.resources.title_addMoney
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall

/** The wallet tab's call to add money, shown until the account has been funded. */
@Composable
fun AddMoneyRow(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CodeTheme.shapes.extraSmall)
            .background(color = Color.White.copy(0.05f))
            .clickable(onClick = onClick)
            .padding(CodeTheme.dimens.grid.x3),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Icon(
            modifier = Modifier.align(Alignment.Top).size(24.dp),
            imageVector = Icons.Outlined.AddCircleOutline,
            tint = CodeTheme.colors.textMain,
            contentDescription = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.title_addMoney),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = stringResource(Res.string.subtitle_addMoney),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}
