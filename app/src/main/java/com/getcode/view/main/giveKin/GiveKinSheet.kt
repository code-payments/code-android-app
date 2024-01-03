package com.getcode.view.main.giveKin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import com.getcode.LocalNetwork
import com.getcode.R
import com.getcode.models.Bill
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.CurrencySelectionModal
import com.getcode.navigation.screens.GiveKinModal
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.util.AnimationUtils
import com.getcode.util.RepeatOnLifecycle
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.CodeKeyPad
import com.getcode.view.main.connectivity.NetworkConnectionViewModel

@Preview
@Composable
fun GiveKinSheet(
    viewModel: GiveKinSheetViewModel = hiltViewModel(),
    connectionViewModel: NetworkConnectionViewModel = hiltViewModel(),
    billToSend: (Bill) -> Unit = { },
) {
    val context = LocalContext.current
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()
    val connectionState by connectionViewModel.connectionStatus.collectAsState()

    RepeatOnLifecycle(
        targetState = Lifecycle.State.RESUMED,
        screen = GiveKinModal,
    ) {
        viewModel.reset()
    }

    val networkUtils = LocalNetwork.current
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
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
                        navigator.show(CurrencySelectionModal)
//                        viewModel.setCurrencySelectorVisible(true)
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
                            if (!networkUtils.isAvailable()) {
                                ErrorUtils.showNetworkError(context)
                                return@CodeButton
                            }

                            val amount = viewModel.onSubmit() ?: return@CodeButton
                            navigator.hideWithResult(Bill.Cash(amount))
                        },
                        enabled = dataState.continueEnabled,
                        text = stringResource(R.string.action_next),
                        buttonState = ButtonState.Filled,
                    )
                }
            }
        }
    }
}