package com.getcode.view.main.requestKin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import cafe.adriel.voyager.navigator.currentOrThrow
import com.getcode.LocalSession
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.CurrencySelectionModal
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeKeyPad
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.ui.components.text.AmountArea
import kotlinx.coroutines.launch

@Preview
@Composable
fun RequestKinScreen(
    viewModel: RequestKinViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.state.collectAsState()
    val composeScope = rememberCoroutineScope()

    val networkObserver = LocalNetworkObserver.current
    val networkState by networkObserver.state.collectAsState()

    val session = LocalSession.currentOrThrow

    Column(
        modifier = Modifier.fillMaxSize()
            .padding(bottom = CodeTheme.dimens.grid.x4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val color =
            if (dataState.amountModel.amountKin > dataState.amountModel.buyLimitKin) CodeTheme.colors.errorText else CodeTheme.colors.brandLight
        Box(
            modifier = Modifier.weight(0.65f)
        ) {
            AmountArea(
                amountPrefix = dataState.amountModel.amountPrefix,
                amountSuffix = dataState.amountModel.amountSuffix,
                amountText = dataState.amountModel.amountText,
                captionText = dataState.amountModel.captionText,
                isAltCaption = dataState.amountModel.isCaptionConversion,
                altCaptionColor = color,
                currencyResId = dataState.currencyModel.selectedCurrency?.resId,
                uiModel = dataState.amountAnimatedModel,
                isAnimated = true,
                networkState = networkState,
                textStyle = CodeTheme.typography.displayLarge,
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .align(Alignment.Center)
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
                if (!networkObserver.isConnected) {
                    ErrorUtils.showNetworkError(context)
                    return@CodeButton
                }

                composeScope.launch {
                    val amount = viewModel.onSubmit() ?: return@launch
                    session.presentRequest(amount = amount, payload = null, request = null)
                    navigator.hide()
                }
            },
            enabled = dataState.continueEnabled,
            text = stringResource(R.string.action_next),
            buttonState = ButtonState.Filled,
        )
    }
}