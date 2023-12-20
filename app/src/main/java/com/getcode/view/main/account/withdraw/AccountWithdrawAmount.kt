package com.getcode.view.main.account.withdraw

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.R
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.manager.AnalyticsManager
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.sheetHeight
import com.getcode.util.AnimationUtils
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.CodeKeyPad
import com.getcode.view.main.giveKin.AmountArea
import com.getcode.view.main.giveKin.CurrencyList

@Composable
fun AccountWithdrawAmount(navController: NavController) {
    val viewModel = hiltViewModel<AccountWithdrawAmountViewModel>()
    val dataState by viewModel.uiFlow.collectAsState()

    AnalyticsScreenWatcher(
        lifecycleOwner = LocalLifecycleOwner.current,
        event = AnalyticsManager.Screen.Withdraw
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight)
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = !dataState.currencySelectorVisible,
            modifier = Modifier.fillMaxSize(),
            enter = AnimationUtils.animationBackEnter,
            exit = AnimationUtils.animationBackExit
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
                        viewModel.setCurrencySelectorVisible(true)
                    }

                    CodeKeyPad(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(bottom = 20.dp)
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
                            .padding(horizontal = 20.dp)
                            .constrainAs(button) {
                                bottom.linkTo(parent.bottom)
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                            },
                        onClick = {
                           viewModel.onSubmit(navController)
                        },
                        enabled = dataState.continueEnabled,
                        text = stringResource(R.string.action_next),
                        buttonState = ButtonState.Filled,
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = dataState.currencySelectorVisible,
            modifier = Modifier.fillMaxSize(),
            enter = AnimationUtils.animationFrontEnter,
            exit = AnimationUtils.animationFrontExit
        ) {
            CurrencyList(
                dataState.currencyModel,
                viewModel::onUpdateCurrencySearchFilter,
                viewModel::onSelectedCurrencyChanged,
                viewModel::setCurrencySelectorVisible,
                viewModel::onRecentCurrencyRemoved,
            )
        }
    }

    if (dataState.currencySelectorVisible) {
        BackHandler {
            viewModel.setCurrencySelectorVisible(false)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.init()
    }
}