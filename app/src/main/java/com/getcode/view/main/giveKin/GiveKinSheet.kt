package com.getcode.view.main.giveKin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.LocalNetworkObserver
import com.getcode.R
import com.getcode.models.Bill
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.CurrencySelectionModal
import com.getcode.theme.Alert
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.CodeKeyPad
import com.getcode.view.main.connectivity.NetworkConnectionViewModel
import kotlinx.coroutines.launch

@Preview
@Composable
fun GiveKinSheet(
    viewModel: GiveKinSheetViewModel = hiltViewModel(),
    connectionViewModel: NetworkConnectionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()
    val connectionState by connectionViewModel.connectionStatus.collectAsState()
    val composeScope = rememberCoroutineScope()

    val networkObserver = LocalNetworkObserver.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val color =
            if (dataState.amountModel.balanceKin < dataState.amountModel.amountKin.toKinValueDouble()) Alert else BrandLight

        Box(
            modifier = Modifier.weight(1f)
        ) {
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
                modifier = Modifier.align(Alignment.Center)
            ) {
                navigator.show(CurrencySelectionModal)
            }
        }

        Box(
            modifier = Modifier.weight(1f)
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
                if (!networkObserver.isConnected) {
                    ErrorUtils.showNetworkError(context)
                    return@CodeButton
                }

                composeScope.launch {
                    val amount = viewModel.onSubmit() ?: return@launch
                    navigator.hideWithResult(Bill.Cash(amount))
                }
            },
            enabled = dataState.continueEnabled,
            text = stringResource(R.string.action_next),
            buttonState = ButtonState.Filled,
        )
    }
}