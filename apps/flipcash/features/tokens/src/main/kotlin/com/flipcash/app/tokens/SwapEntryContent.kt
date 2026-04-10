package com.flipcash.app.tokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.onramp.LocalExternalWalletState
import com.flipcash.app.tokens.internal.SwapEntryScreenContent
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.features.tokens.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
internal fun SwapEntryContent(
    purpose: SwapPurpose,
) {
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()
    val viewModel = flowSharedViewModel<SwapViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val externalWalletOnRamp = LocalExternalWalletState.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            isInModal = true,
            title = when (purpose) {
                is SwapPurpose.BalanceIncrease -> stringResource(R.string.title_amountToBuy)
                is SwapPurpose.BalanceDecrease -> stringResource(R.string.title_amountToSell)
            },
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = {
                if (state.buyProgress.loading) {
                    // swallow
                } else {
                    flowNavigator.back()
                }
            }
        )

        SwapEntryScreenContent(viewModel)
    }

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(SwapViewModel.Event.OnPurposeChanged(purpose))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.ShowSellReceipt>()
            .onEach {
                flowNavigator.navigateTo(SwapStep.SellReceipt)
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.CreateAndSendTransactionToWallet>()
            .onEach { (token, amount) ->
                externalWalletOnRamp.tokenToPurchase = token
                externalWalletOnRamp.amount = amount
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.Exit>()
            .onEach {
                flowNavigator.exitCanceled()
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OnPurchaseSubmitted>()
            .map { it.swapId }
            .onEach { swapId ->
                flowNavigator.navigateTo(SwapStep.Processing(swapId))
            }.launchIn(this)
    }

    // Navigate to pending processing step from ExternalWalletOnRampHandler
    val pendingNav = externalWalletOnRamp.pendingNavigation
    LaunchedEffect(pendingNav) {
        if (pendingNav is AppRoute.Token.TxProcessing) {
            flowNavigator.navigateTo(
                SwapStep.Processing(pendingNav.swapId, pendingNav.awaitExternalWallet)
            )
            externalWalletOnRamp.pendingNavigation = null
        }
    }
}
