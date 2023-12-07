package com.getcode.view.main.giveKin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.App
import com.getcode.R
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.NetworkUtils
import com.getcode.view.components.BackType
import com.getcode.view.components.BaseCodeBottomsheet
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.CodeKeyPad
import com.getcode.view.main.connectivity.NetworkConnectionViewModel
import com.getcode.view.main.home.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GiveKinSheet(
    giveKinSheetState: ModalBottomSheetState,
    isVisible: Boolean = true,
    onBack: () -> Unit = {}
) {
    val viewModel = hiltViewModel<GiveKinSheetViewModel>()
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

    //Give Kin Bottom sheet
    BaseCodeBottomsheet(
        state = giveKinSheetState,
        title = stringResource(id = R.string.action_giveKin),
        backType = BackType.Close,
        onBack = onBack
    ) {
        GiveKinSheet(
            viewModel = viewModel,
            dataState = dataState,
            isVisible = isVisible,
            onBack = onBack,
            onShowCurrencySelector = {
                viewModel.setCurrencySelectorVisible(true)
                scope.launch { currencySheetState.show() }
            }
        )
    }

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
}

@Composable
fun GiveKinSheet(
    viewModel: GiveKinSheetViewModel,
    dataState: GiveKinSheetUiModel,
    isVisible: Boolean = true,
    onShowCurrencySelector: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val connectionViewModel = hiltViewModel<NetworkConnectionViewModel>()
    val homeViewModel = hiltViewModel<HomeViewModel>()
    val connectionState by connectionViewModel.connectionStatus.collectAsState()
    //Here we will hold the "has opened" of the balance sheet so one it
    //enters composition it will remain composed.
    var hasGiveKinSheetOpened by rememberSaveable {
        mutableStateOf(false)
    }

    //We want to refresh the data when the sheet opens
    LaunchedEffect(isVisible) {
        if (isVisible) {
            hasGiveKinSheetOpened = isVisible
            viewModel.reset()
        }
        viewModel.init()
    }

    //Skip composition if the sheet has not been set to open
    //by the sheet controller in HomeScan
    if (!hasGiveKinSheetOpened) {
        return
    }
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
                onShowCurrencySelector()
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
                        //top.linkTo(keyPad.bottom)
                    },
                onClick = {
                    if (!NetworkUtils.isNetworkAvailable(App.getInstance())) {
                        ErrorUtils.showNetworkError()
                        return@CodeButton
                    }
                    val amount = viewModel.onSubmit() ?: return@CodeButton
                    onBack()
                    homeViewModel.showBill(amount)
                },
                enabled = dataState.continueEnabled,
                text = stringResource(R.string.action_next),
                buttonState = ButtonState.Filled,
            )
        }
    }
}