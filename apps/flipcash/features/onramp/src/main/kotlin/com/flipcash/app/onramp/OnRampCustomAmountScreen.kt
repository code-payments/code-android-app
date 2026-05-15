package com.flipcash.app.onramp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.getcode.solana.keys.Mint

import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.app.onramp.internal.screens.OnRampAmountScreen
import com.flipcash.features.onramp.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun OnRampCustomAmountScreen(mint: Mint) {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<OnRampViewModel>()

    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_amountToBuy),
            isInModal = true,
            backButton = state.orderLookup.isIdle,
            onBackIconClicked = { navigator.pop() },
            titleAlignment = Alignment.CenterHorizontally,
        )
        OnRampAmountScreen(viewModel)
    }

    LaunchedEffect(Unit) {
        if (mint != null) {
            viewModel.dispatchEvent(OnRampViewModel.Event.OnMintChanged(mint))
        }
    }

    val externalWalletOnRampController = LocalExternalWalletOnRampController.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<OnRampViewModel.Event.CreateAndSendTransactionToWallet>()
            .map { it.amount }
            .onEach { externalWalletOnRampController.setAmount(it) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<OnRampViewModel.Event.OnVerificationNeeded>()
            .onEach { (phone, email) ->
                navigator.push(
                    AppRoute.Verification(
                        origin = AppRoute.Token.OnRamp(mint),
                        includePhone = phone,
                        includeEmail = email,
                    )
                )
            }.launchIn(this)
    }

    val coinbaseOnRampController = LocalCoinbaseOnRampController.current
    LaunchedEffect(Unit) {
        coinbaseOnRampController.pendingNavigation.collect { route ->
            navigator.push(route)
        }
    }
}
