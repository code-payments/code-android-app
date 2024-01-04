package com.getcode.view.main.account.withdraw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.CurrencySelectionModal
import com.getcode.navigation.screens.WithdrawalAmountScreen
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.sheetHeight
import com.getcode.util.AnimationUtils
import com.getcode.util.RepeatOnLifecycle
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.CodeKeyPad
import com.getcode.view.main.currency.CurrencyViewModel
import com.getcode.view.main.giveKin.AmountArea

@Composable
fun AccountWithdrawAmount(
    viewModel: AccountWithdrawAmountViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()


    RepeatOnLifecycle(
        targetState = Lifecycle.State.RESUMED,
        screen = WithdrawalAmountScreen
        ) {
        viewModel.reset()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                val (amountArea, keyPad, button) = createRefs()
                val color =
                    if (dataState.amountModel.balanceKin < dataState.amountModel.amountKin.toKinValueDouble()) Alert else BrandLight

                AmountArea(
                    amountPrefix = dataState.amountModel.amountPrefix,
                    amountSuffix = dataState.amountModel.amountSuffix,
                    amountText = dataState.amountModel.amountText,
                    captionText = dataState.amountModel.captionText,
                    isAltCaption = dataState.amountModel.isCaptionConversion,
                    altCaptionColor = color,
                    currencyResId = dataState.currencyModel.selectedCurrencyResId,
                    uiModel = dataState.amountAnimatedModel,
                    isAnimated = true,
                    modifier = Modifier.constrainAs(amountArea) {
                        top.linkTo(parent.top)
                        bottom.linkTo(keyPad.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
                ) {
                    navigator.show(CurrencySelectionModal)
                }

                CodeKeyPad(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(bottom = CodeTheme.dimens.inset)
                        .constrainAs(keyPad) {
                            bottom.linkTo(button.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            top.linkTo(amountArea.bottom)
                        },
                    onNumber = viewModel::onNumber,
                    onClear = viewModel::onBackspace,
                    onDecimal = viewModel::onDot,
                    isDecimal = true
                )

                CodeButton(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .constrainAs(button) {
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        },
                    onClick = {
                        viewModel.onSubmit(navigator)
                    },
                    enabled = dataState.continueEnabled,
                    text = stringResource(R.string.action_next),
                    buttonState = ButtonState.Filled,
                )
            }
        }
    }
}