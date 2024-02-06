package com.getcode.view.main.account.withdraw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import com.getcode.models.Bill
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.CurrencySelectionModal
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.displayLarge
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.CodeKeyPad
import com.getcode.view.main.giveKin.AmountArea
import kotlinx.coroutines.launch

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
                currencyResId = dataState.currencyModel.selectedCurrencyResId,
                uiModel = dataState.amountAnimatedModel,
                isAnimated = true,
                textStyle = CodeTheme.typography.displayLarge,
                ) {
                navigator.show(CurrencySelectionModal)
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
            isDecimal = true
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