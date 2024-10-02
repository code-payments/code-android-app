package com.getcode.view.main.account.withdraw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.CurrencySelectionModal
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeKeyPad
import com.getcode.view.main.giveKin.AmountArea

@Composable
fun AccountWithdrawAmount(
    viewModel: AccountWithdrawAmountViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = CodeTheme.dimens.grid.x4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val color =
            if (dataState.amountModel.balanceKin < dataState.amountModel.amountKin.toKinValueDouble()) Alert else BrandLight

        Box(
            modifier = Modifier.weight(0.5f)
        ) {
            AmountArea(
                modifier = Modifier.align(Alignment.Center),
                amountPrefix = dataState.amountModel.amountPrefix,
                amountSuffix = dataState.amountModel.amountSuffix,
                amountText = dataState.amountModel.amountText,
                captionText = dataState.amountModel.captionText,
                isAltCaption = dataState.amountModel.isCaptionConversion,
                altCaptionColor = color,
                currencyResId = dataState.currencyModel.selectedCurrency?.resId,
                uiModel = dataState.amountAnimatedModel,
                isAnimated = true,
                textStyle = CodeTheme.typography.displayLarge,
                ) {
                navigator.push(CurrencySelectionModal())
            }
        }

        CodeKeyPad(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.inset)
                .weight(1f),
            onNumber = viewModel::onNumber,
            onClear = viewModel::onBackspace,
            onDecimal = viewModel::onDot,
            isDecimal = dataState.amountModel.isDecimalAllowed
        )

        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            onClick = {
                viewModel.onSubmit(navigator)
            },
            enabled = dataState.continueEnabled,
            text = stringResource(R.string.action_next),
            buttonState = ButtonState.Filled,
        )
    }
}