package com.getcode.view.main.account.withdraw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.CurrencySelectionModal
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.CodeKeyPad
import com.getcode.view.main.giveKin.AmountArea

@Composable
fun AccountWithdrawAmount(
    viewModel: AccountWithdrawAmountViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val color =
            if (dataState.amountModel.balanceKin < dataState.amountModel.amountKin.toKinValueDouble()) Alert else BrandLight

        Box(
            modifier = Modifier.weight(0.35f)
        ) {
            AmountArea(
                modifier = Modifier.align(Alignment.Center),
                amountPrefix = dataState.amountModel.amountPrefix,
                amountSuffix = dataState.amountModel.amountSuffix,
                amountText = dataState.amountModel.amountText,
                captionText = dataState.amountModel.captionText,
                isAltCaption = dataState.amountModel.isCaptionConversion,
                altCaptionColor = color,
                currencyResId = dataState.currencyModel.selectedCurrencyResId,
                uiModel = dataState.amountAnimatedModel,
                isAnimated = true,
            ) {
                navigator.show(CurrencySelectionModal)
            }
        }

        Box(
        ) {
            CodeKeyPad(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CodeTheme.dimens.inset)
                    .align(Alignment.BottomCenter),
                onNumber = viewModel::onNumber,
                onClear = viewModel::onBackspace,
                onDecimal = viewModel::onDot,
                isDecimal = true
            )
        }


        CodeButton(
            modifier = Modifier
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