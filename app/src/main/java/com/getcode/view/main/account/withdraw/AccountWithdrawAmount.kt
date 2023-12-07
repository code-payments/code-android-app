package com.getcode.view.main.account.withdraw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.R
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.sheetHeight
import com.getcode.util.AnimationUtils
import com.getcode.view.components.BackType
import com.getcode.view.components.BaseCodeBottomsheet
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.CodeKeyPad
import com.getcode.view.main.giveKin.AmountArea
import com.getcode.view.main.giveKin.CurrencyList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AccountWithdrawAmount(navController: NavController) {
    val viewModel = hiltViewModel<AccountWithdrawAmountViewModel>()
    val dataState by viewModel.uiFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val currencySheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
        animationSpec = remember {
            Animatable(0f)
                .run {
                    TweenSpec(durationMillis = 400, easing = LinearOutSlowInEasing)
                }
        }
    )

    //Show AccountWithdrawAmountSheet
    AccountWithdrawAmount(
        viewModel = viewModel,
        dataState = dataState,
        onShowCurrencySelector = {
            viewModel.setCurrencySelectorVisible(false)
            scope.launch { currencySheetState.hide() }
        },
        onSubmit = { viewModel.onSubmit(navController) }
    )

    //Currency Selector Bottomsheet
    BaseCodeBottomsheet(
        state = currencySheetState,
        title = stringResource(id = R.string.title_selectCurrency),
        backType = BackType.Back,
        onBack = {
            viewModel.setCurrencySelectorVisible(false)
            scope.launch { currencySheetState.hide() }
        }
    ) {
        CurrencyList(
            dataState.currencyModel,
            viewModel::onUpdateCurrencySearchFilter,
            viewModel::onSelectedCurrencyChanged,
            viewModel::setCurrencySelectorVisible,
            viewModel::onRecentCurrencyRemoved,
        )
    }

    LaunchedEffect(Unit) {
        viewModel.init()
    }
}

@Composable
fun AccountWithdrawAmount(
    viewModel: AccountWithdrawAmountViewModel,
    dataState: AccountWithdrawAmountUiModel,
    onShowCurrencySelector: () -> Unit = {},
    onSubmit: () -> Unit
) {
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
                        onShowCurrencySelector()
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
                        onClick = onSubmit,
                        enabled = dataState.continueEnabled,
                        text = stringResource(R.string.action_next),
                        buttonState = ButtonState.Filled,
                    )
                }
            }
        }
    }
}
