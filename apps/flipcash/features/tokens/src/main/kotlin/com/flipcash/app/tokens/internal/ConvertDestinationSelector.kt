package com.flipcash.app.tokens.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.theme.CodeTheme

/**
 * "Convert to  [🪙 Currency ⌄]" — the destination picker that sits between the entered amount and
 * the keypad on the Convert amount screen. Tapping it pushes the currency list as a flow step.
 */
@Composable
internal fun ConvertDestinationSelector(
    destination: TokenWithBalance?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    destination ?: return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.label_convertTo),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TokenIconWithName(
                token = destination.token,
                imageSize = CodeTheme.dimens.staticGrid.x6,
                textStyle = CodeTheme.typography.textMedium,
                spacing = CodeTheme.dimens.grid.x2,
                displayName = { destination.displayName },
            )

            Icon(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
        }
    }
}
