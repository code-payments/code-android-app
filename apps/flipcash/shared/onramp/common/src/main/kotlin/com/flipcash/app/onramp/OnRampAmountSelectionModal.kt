package com.flipcash.app.onramp

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.onramp.internal.InternalOnRampAmountController
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.services.analytics.StubFlipcashAnalytics
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.shared.onramp.common.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.toFiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.Modal
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton


private val amounts = listOf(
    10.toFiat(),
    25.toFiat(),
    50.toFiat(),
    75.toFiat(),
    100.toFiat(),
)

@Composable
fun OnRampAmountSelectionModal(
    provider: OnRampProvider,
    selectedAmount: OnRampAmount?,
    onAmountSelected: (Fiat?) -> Unit,
    onCustomSelected: () -> Unit,
    onConfirm: () -> Unit,
) {
    Modal {
        Column {
            AppBarWithTitle(
                title = when (provider) {
                    is OnRampProvider.Coinbase -> stringResource(R.string.title_addCashWithDebitCard)
                    else -> ""
                },
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
            )

            LazyVerticalGrid(
                modifier = Modifier.fillMaxWidth(),
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    horizontal = CodeTheme.dimens.grid.x4,
                    vertical = CodeTheme.dimens.inset
                ),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                items(amounts) { amount ->
                    val isSelected =
                        selectedAmount is OnRampAmount.Predefined && selectedAmount.fiat == amount
                    PresetAmount(
                        modifier = Modifier.fillMaxSize(),
                        amount = amount,
                        isSelected = isSelected,
                    ) {
                        onAmountSelected(amount.takeIf { !isSelected })
                    }
                }

                item {
                    val isSelected = selectedAmount == OnRampAmount.Custom
                    CustomAmount(
                        modifier = Modifier.fillMaxSize(),
                        isSelected = isSelected,
                    ) { onCustomSelected() }
                }
            }

            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                enabled = selectedAmount != null,
                buttonState = ButtonState.Filled,
                text = when (provider) {
                    is OnRampProvider.Coinbase -> "Add with Google Pay"
                    else -> stringResource(R.string.action_next)
                }
            ) { onConfirm() }
        }
    }
}

@Composable
private fun PresetAmount(
    amount: Fiat,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ClickableCell(
        text = amount.formatted(formatting = Fiat.Formatting.Truncated),
        isSelected = isSelected,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
private fun CustomAmount(
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    ClickableCell(
        text = "Custom",
        isSelected = isSelected,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
private fun ClickableCell(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        if (isSelected) Color.White.copy(alpha = 0.20f) else CodeTheme.colors.surfaceVariant
    )

    val borderColor by animateColorAsState(
        if (isSelected) Color.White else Color.Transparent
    )
    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = CodeTheme.shapes.extraSmall,
            ).border(
                width = CodeTheme.dimens.border,
                color = borderColor,
                shape = CodeTheme.shapes.extraSmall
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = Modifier.fillMaxSize().padding(vertical = CodeTheme.dimens.grid.x4),
            text = text,
            maxLines = 1,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@Preview
private fun OnRampAmountSelectionModal_Preview() {
    val controller = InternalOnRampAmountController(StubFlipcashAnalytics()).apply {
        requestAmountSelection(OnRampProvider.Phantom)
        selectAmount(OnRampAmount.Predefined(amounts.first()))
    }

    FlipcashDesignSystem {
        CompositionLocalProvider(LocalOnRampAmountController provides controller) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.background)) {
                OnRampAmountScaffold {

                }
            }
        }
    }
}