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
 * "Label  [🪙 Currency ⌄]" — the inline currency picker that sits between the entered amount and
 * the keypad. Tapping the trailing chip pushes a currency list as a flow step.
 *
 * Convert and the v2 Get both pick a currency here; only the label and which side of the trade it
 * names differ, so they share this row via [ConvertDestinationSelector] and [BuyFundingSelector].
 */
@Composable
private fun TokenSelectorRow(
    label: String,
    selected: TokenWithBalance?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    selected ?: return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
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
                token = selected.token,
                imageSize = CodeTheme.dimens.staticGrid.x6,
                textStyle = CodeTheme.typography.textMedium,
                spacing = CodeTheme.dimens.grid.x2,
                displayName = { selected.displayName },
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

/** "Convert to [🪙 Currency ⌄]" — what a conversion lands in. */
@Composable
internal fun ConvertDestinationSelector(
    destination: TokenWithBalance?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = TokenSelectorRow(
    label = stringResource(R.string.label_convertTo),
    selected = destination,
    onClick = onClick,
    modifier = modifier,
)

/** "Get with [🪙 Currency ⌄]" — what a v2 Get is paid from. */
@Composable
internal fun BuyFundingSelector(
    funding: TokenWithBalance?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = TokenSelectorRow(
    label = stringResource(R.string.label_getWith),
    selected = funding,
    onClick = onClick,
    modifier = modifier,
)
