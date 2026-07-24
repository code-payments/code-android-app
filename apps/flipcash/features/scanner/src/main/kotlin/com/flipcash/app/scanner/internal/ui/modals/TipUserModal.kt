package com.flipcash.app.scanner.internal.ui.modals

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.core.tipping.LocalTipCoordinator
import com.flipcash.app.core.tipping.TipAmount
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.features.scanner.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.Modal
import com.getcode.ui.components.SlideToConfirm

@Composable
internal fun TipUserModal(
    card: Scannable.TipCard,
) {
    val tip = LocalTipCoordinator.current
    val navigator = LocalCodeNavigator.current

    val selection by tip.selection.collectAsState()

    // A newly presented tip card starts with a clean selection.
    LaunchedEffect(card) { tip.selectAmount(null) }

    Modal(
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
    ) {
        Text(
            text = stringResource(id = R.string.title_sendTip),
            style = CodeTheme.typography.displaySmall,
            color = CodeTheme.colors.textMain,
        )

        PresetOptions(
            // Presets follow the selected region reactively (see TippingCoordinator.selection).
            presets = selection.presets,
            selected = selection.amount?.value,
            modifier = Modifier.fillMaxWidth(),
            onPresetClicked = { tip.selectAmount(TipAmount.Preset(it)) },
            onCustomClicked = { navigator.openAsSheet(AppRoute.Sheets.TipAmountEntry) },
        )

        TipTokenRow(
            token = selection.token,
            modifier = Modifier.fillMaxWidth(),
            onSelectToken = {
                navigator.openAsSheet(AppRoute.Sheets.TokenSelection(TokenPurpose.Tip( selection.minimum)))
            },
        )

        SlideToConfirm(
            onConfirm = { tip.confirmTip() },
            modifier = Modifier.fillMaxWidth(),
            enabled = selection.canConfirm,
            isLoading = selection.sendState.loading,
            isSuccess = selection.sendState.success,
            label = stringResource(R.string.action_swipeToTip),
        )
    }
}

/** "of [token]" row — the token the tip will be sent in; tapping the pill opens the token picker. */
@Composable
private fun TipTokenRow(
    token: Token?,
    onSelectToken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            CodeTheme.dimens.grid.x2,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.label_of),
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
}

@Composable
private fun PresetOptions(
    presets: List<Fiat>,
    selected: Fiat?,
    modifier: Modifier = Modifier,
    onPresetClicked: (Fiat) -> Unit,
    onCustomClicked: () -> Unit,
) {
    val isCustomSelected = selected != null && selected !in presets

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.fastForEach { preset ->
            TipAmount(
                fiat = preset,
                selected = preset == selected,
                onClick = { onPresetClicked(preset) },
            )
        }

        // Custom-amount slot: shows the entered value when a custom amount is set, otherwise a
        // "more" affordance that opens the amount-entry sheet.
        TipAmount(
            fiat = selected.takeIf { isCustomSelected },
            selected = isCustomSelected,
            onClick = onCustomClicked,
        )
    }
}

@Composable
private fun RowScope.TipAmount(
    fiat: Fiat?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundAlpha by animateFloatAsState(
        if (selected) 1f else 0.10f
    )

    val foregroundColor by animateColorAsState(
        if (selected) Color.Black else Color.White
    )

    val customAmountDescription = stringResource(R.string.content_description_customTipAmount)

    Box(
        modifier = Modifier
            .weight(1f)
            .clip(CodeTheme.shapes.medium)
            .background(
                color = Color.White.copy(alpha = backgroundAlpha),
                shape = CodeTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(vertical = CodeTheme.dimens.grid.x4)
            .semantics {
                contentDescription = fiat?.formatted(rule = Fiat.FormattingRule.Truncated)
                    ?: customAmountDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        if (fiat != null) {
            Text(
                text = fiat.formatted(rule = Fiat.FormattingRule.Truncated),
                style = CodeTheme.typography.textLarge,
                color = foregroundColor,
                textAlign = TextAlign.Center,
            )
        } else {
            Image(
                modifier = Modifier
                    .requiredSize(CodeTheme.dimens.staticGrid.x6),
                imageVector = Icons.Rounded.MoreHoriz,
                colorFilter = ColorFilter.tint(foregroundColor),
                contentDescription = null,
            )
        }
    }
}
