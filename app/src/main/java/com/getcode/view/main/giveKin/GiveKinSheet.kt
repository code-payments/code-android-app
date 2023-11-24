package com.getcode.view.main.giveKin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.App
import com.getcode.R
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.sheetHeight
import com.getcode.util.AnimationUtils
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.NetworkUtils
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.CodeKeyPad
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.connectivity.NetworkConnectionViewModel
import com.getcode.view.main.home.HomeViewModel

@Preview
@Composable
fun GiveKinSheet(
    isVisible: Boolean = true,
    onClose: () -> Unit = {},
    onCloseQuickly: () -> Unit = {}
) {
    val giveKinViewModel = hiltViewModel<GiveKinSheetViewModel>()
    val connectionViewModel = hiltViewModel<NetworkConnectionViewModel>()
    val homeViewModel = hiltViewModel<HomeViewModel>()
    val dataState by giveKinViewModel.uiFlow.collectAsState()
    val connectionState by connectionViewModel.connectionStatus.collectAsState()
    val currencySelectorVisible = dataState.currencySelectorVisible

    //Here we will hold the "has opened" of the balance sheet so one it
    //enters composition it will remain composed.
    var hasGiveKinSheetOpened by rememberSaveable {
        mutableStateOf(false)
    }

    //We want to refresh the data when the sheet opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            hasGiveKinSheetOpened = isVisible
            giveKinViewModel.reset()
        }
        giveKinViewModel.init()
    }


    //Skip composition if the sheet has not been set to open
    //by the sheet controller in HomeScan
    if (!hasGiveKinSheetOpened) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight)
    ) {
        SheetTitle(
            modifier = Modifier.padding(horizontal = 20.dp),
            title = stringResource(id = if (currencySelectorVisible) R.string.title_selectCurrency else R.string.action_giveKin),
            onCloseIconClicked = onClose,
            onBackIconClicked = { giveKinViewModel.setCurrencySelectorVisible(false) },
            backButton = currencySelectorVisible,
            closeButton = !currencySelectorVisible
        )
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !currencySelectorVisible,
                modifier = Modifier.fillMaxSize(),
                enter = AnimationUtils.animationBackEnter,
                exit = AnimationUtils.animationBackExit
            ) {
                Box {
                    ConstraintLayout(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val (amountArea, keyPad, button) = createRefs()

                        val color =
                            if (dataState.amountModel.isInsufficient) Alert else BrandLight

                        AmountArea(
                            amountPrefix = dataState.amountModel.amountPrefix,
                            amountSuffix = dataState.amountModel.amountSuffix,
                            amountText = dataState.amountModel.amountText,
                            captionText = dataState.amountModel.captionText,
                            isAltCaption = dataState.amountModel.isCaptionConversion,
                            altCaptionColor = color,
                            currencyResId = dataState.currencyModel.selectedCurrencyResId,
                            uiModel = dataState.amountAnimatedModel,
                            connectionState = connectionState,
                            isAnimated = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .constrainAs(amountArea) {
                                    top.linkTo(parent.top)
                                    bottom.linkTo(keyPad.top)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                        ) {
                            giveKinViewModel.setCurrencySelectorVisible(true)
                        }

                        CodeKeyPad(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(bottom = 5.dp)
                                .constrainAs(keyPad) {
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                    linkTo(keyPad.top, amountArea.bottom, bias = 0.0F)
                                    linkTo(keyPad.bottom, button.top, bias = 1.0F)
                                },
                            onNumber = giveKinViewModel::onNumber,
                            onClear = giveKinViewModel::onBackspace,
                            onDecimal = giveKinViewModel::onDot,
                            isDecimal = true
                        )

                        CodeButton(
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .constrainAs(button) {
                                    bottom.linkTo(parent.bottom)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                    //top.linkTo(keyPad.bottom)
                                },
                            onClick = {
                                if (!NetworkUtils.isNetworkAvailable(App.getInstance())) {
                                    ErrorUtils.showNetworkError()
                                    return@CodeButton
                                }

                                val amount = giveKinViewModel.onSubmit() ?: return@CodeButton
                                onCloseQuickly()
                                homeViewModel.showBill(amount)
                            },
                            enabled = dataState.continueEnabled,
                            text = stringResource(R.string.action_next),
                            buttonState = ButtonState.Filled,
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = currencySelectorVisible,
                modifier = Modifier.fillMaxSize(),
                enter = AnimationUtils.animationFrontEnter,
                exit = AnimationUtils.animationFrontExit
            ) {
                CurrencyList(
                    dataState.currencyModel,
                    giveKinViewModel::onUpdateCurrencySearchFilter,
                    giveKinViewModel::onSelectedCurrencyChanged,
                    giveKinViewModel::setCurrencySelectorVisible,
                    giveKinViewModel::onRecentCurrencyRemoved,
                )
            }
        }
    }
    if (dataState.currencySelectorVisible) {
        BackHandler {
            giveKinViewModel.setCurrencySelectorVisible(false)
        }
    }
}