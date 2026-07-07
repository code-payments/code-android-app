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
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.core.verification.VerificationResult
import com.flipcash.app.onramp.CoinbaseOnRampCompletion
import com.flipcash.app.onramp.LocalCoinbaseOnRampController
import com.flipcash.app.tokens.internal.SwapEntryScreenContent
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.navigateForResult
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
internal fun SwapEntryScreen(
    purpose: SwapPurpose,
    initialAmount: Fiat? = null,
) {
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()
    val navigator = LocalCodeNavigator.current
    val viewModel = flowSharedViewModel<SwapViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val coinbaseOnRampController = LocalCoinbaseOnRampController.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = when (purpose) {
                is SwapPurpose.Buy if purpose.fundingSource != FundingSource.Flexible ->
                    stringResource(R.string.title_amountToAdd)
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
        if (initialAmount != null) {
            viewModel.dispatchEvent(SwapViewModel.Event.OnInitialAmountProvided(initialAmount))
        }
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
            .filterIsInstance<SwapViewModel.Event.Exit>()
            .onEach {
                flowNavigator.exitCanceled()
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OnPurchaseSubmitted>()
            .onEach {
                flowNavigator.navigateTo(SwapStep.Processing)
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OnVerificationNeeded>()
            .onEach { (phone, email) ->
                val mint = (viewModel.stateFlow.value.purpose as? SwapPurpose.Buy)?.mint ?: return@onEach
                navigator.navigateForResult<VerificationResult>(
                    AppRoute.Verification(
                        origin = AppRoute.Token.Swap(SwapPurpose.Buy(mint)),
                        includePhone = phone,
                        includeEmail = email,
                    )
                ) { result ->
                    if (result is NavResultOrCanceled.ReturnValue &&
                        result.value is VerificationResult.Success) {
                        viewModel.dispatchEvent(SwapViewModel.Event.OnAmountConfirmed)
                    }
                }
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.PhantomSelected>()
            .onEach {
                flowNavigator.navigateTo(SwapStep.PhantomConnect)
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OtherWalletSelected>()
            .onEach {
                flowNavigator.exitWithResult(SwapResult.OpenDeposit)
            }.launchIn(this)
    }

    LaunchedEffect(Unit) {
        coinbaseOnRampController.pendingCompletion.collect { completion ->
            when (completion) {
                is CoinbaseOnRampCompletion.SwapSubmitted -> {
                    viewModel.dispatchEvent(SwapViewModel.Event.OnSwapIdChanged(completion.swapId))
                    flowNavigator.navigateTo(SwapStep.Processing)
                }
                is CoinbaseOnRampCompletion.DepositSubmitted -> {
                    viewModel.dispatchEvent(SwapViewModel.Event.UpdateProcessingState(loading = true))
                    viewModel.dispatchEvent(SwapViewModel.Event.DepositSubmitted)
                    flowNavigator.navigateTo(SwapStep.Processing)
                }
            }
        }
    }
}
