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
import com.flipcash.app.core.AppRoute
import com.getcode.solana.keys.Mint
import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.app.onramp.internal.OnrampOrder
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
fun OnRampCustomAmountScreen(mint: Mint) {
    val navigator = LocalCodeNavigator.current
    val viewModel = flowScopedViewModel<OnRampViewModel>(key = OnRampFlowTracker.key)
    var order by rememberSaveable { mutableStateOf<OnrampOrder?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_amountToBuy),
            isInModal = true,
            backButton = true,
            onBackIconClicked = { navigator.pop() },
            titleAlignment = Alignment.CenterHorizontally,
        )
        Box {
            order?.let {
                CoinbaseOnRampWebview(
                    orderId = it.orderId,
                    paymentLinkUrl = it.paymentLink,
                    onPaymentSuccess = { orderId ->
                        viewModel.dispatchEvent(OnRampViewModel.Event.OnPaymentSuccess(orderId))
                        order = null
                    },
                    onPaymentFailure = { error ->
                        viewModel.dispatchEvent(OnRampViewModel.Event.OnPaymentError(error))
                        order = null
                    },
                    onCancel = {
                        viewModel.dispatchEvent(OnRampViewModel.Event.OnPaymentCancel)
                        order = null
                    },
                )
            }
            OnRampAmountScreen(viewModel)
        }
    }

    LaunchedEffect(Unit) {
        if (mint != null) {
            viewModel.dispatchEvent(OnRampViewModel.Event.OnMintChanged(mint))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<OnRampViewModel.Event.OnOrderCreated>()
            .map { it.order }
            .onEach { order = it }
            .launchIn(this)
    }

    val externalWalletOnRamp = LocalExternalWalletState.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<OnRampViewModel.Event.CreateAndSendTransactionToWallet>()
            .map { it.amount }
            .onEach { externalWalletOnRamp.amount = it }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<OnRampViewModel.Event.OnVerificationNeeded>()
            .onEach { (phone, email) ->
                navigator.push(
                    AppRoute.Verification(
                        origin = AppRoute.OnRamp.AmountEntry(mint),
                        includePhone = phone,
                        includeEmail = email,
                    )
                )
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<OnRampViewModel.Event.OnBuySubmitted>()
            .map { it.swapId }
            .onEach { swapId ->
                navigator.push(AppRoute.Token.TxProcessing(swapId))
            }.launchIn(this)
    }
}
