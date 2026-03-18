package com.flipcash.app.onramp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.app.onramp.internal.screens.OnRampAmountScreen
import com.flipcash.features.onramp.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.flowScopedViewModel
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun OnRampCustomAmountScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel = flowScopedViewModel<OnRampViewModel>(key = OnRampFlowTracker.key)
    var paymentLink by rememberSaveable { mutableStateOf<String?>(null) }

    Box {
        paymentLink?.let {
            CoinbaseOnRampWebview(
                paymentLinkUrl = it,
                onPaymentSuccess = {
                    paymentLink = null
                    viewModel.dispatchEvent(OnRampViewModel.Event.OnPaymentSuccess)
                },
                onPaymentFailure = {
                    paymentLink = null
                    viewModel.dispatchEvent(OnRampViewModel.Event.OnPaymentError(it))
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppBarWithTitle(
                title = stringResource(R.string.title_amountToDeposit),
                isInModal = true,
                backButton = true,
                onBackIconClicked = { navigator.pop() },
                titleAlignment = Alignment.CenterHorizontally,
            )
            OnRampAmountScreen(viewModel)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<OnRampViewModel.Event.OnPaymentLinkGenerated>()
            .map { it.url }
            .onEach {

            }.launchIn(this)
    }

    val externalWalletOnRamp = LocalExternalWalletState.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<OnRampViewModel.Event.CreateAndSendTransactionToWallet>()
            .map { it.amount }
            .onEach { externalWalletOnRamp.amount = it }
            .launchIn(this)
    }
}
