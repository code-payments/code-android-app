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
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.onramp.LocalExternalWalletState
import com.flipcash.app.tokens.internal.BuySellTokenEntryScreen
import com.flipcash.app.tokens.ui.BuySellSwapTokenViewModel
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.flowScopedViewModel
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun TokenBuySellEntryScreen(
    purpose: TokenSwapPurpose,
) {
    val navigator = LocalCodeNavigator.current
    val viewModel = flowScopedViewModel<BuySellSwapTokenViewModel>(BuySellFlow.key)
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val externalWalletOnRamp = LocalExternalWalletState.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            isInModal = true,
            title = when (purpose) {
                is TokenSwapPurpose.BalanceIncrease -> stringResource(R.string.title_amountToBuy)
                is TokenSwapPurpose.BalanceDecrease -> stringResource(R.string.title_amountToSell)
            },
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = {
                if (state.buyProgress.loading) {
                    // swallow
                } else {
                    navigator.pop()
                }
            }
        )

        BuySellTokenEntryScreen(viewModel)
    }

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(BuySellSwapTokenViewModel.Event.OnPurposeChanged(purpose))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<BuySellSwapTokenViewModel.Event.ShowSellReceipt>()
            .onEach {
                navigator.push(AppRoute.Token.SellReceipt)
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<BuySellSwapTokenViewModel.Event.CreateAndSendTransactionToWallet>()
            .onEach { (token, amount) ->
                externalWalletOnRamp.tokenToPurchase = token
                externalWalletOnRamp.amount = amount
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<BuySellSwapTokenViewModel.Event.Exit>()
            .onEach {
                navigator.popUntil { it is AppRoute.Token.Info }
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<BuySellSwapTokenViewModel.Event.OnPurchaseSubmitted>()
            .map { it.swapId }
            .onEach { swapId ->
                navigator.push(AppRoute.Token.TxProcessing(swapId))
            }.launchIn(this)
    }

    // Navigate to pending routes from ExternalWalletOnRampHandler using the
    // sheet's inner navigator (which the handler can't access directly).
    val pendingNav = externalWalletOnRamp.pendingNavigation
    LaunchedEffect(pendingNav) {
        if (pendingNav is AppRoute.Token.TxProcessing) {
            navigator.push(pendingNav)
            externalWalletOnRamp.pendingNavigation = null
        }
    }
}
