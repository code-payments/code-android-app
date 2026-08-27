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
import com.flipcash.app.tokens.internal.BuyFundingSelector
import com.flipcash.app.tokens.internal.ConvertDestinationSelector
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
import kotlinx.coroutines.flow.filter
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
                is SwapPurpose.Convert -> stringResource(R.string.title_amountToConvert)
                // v2 titles the direct buy after the currency-info tile that opened it and
                // states the amount in the header instead of the title, matching Convert.
                is SwapPurpose.BalanceIncrease if state.isGet -> stringResource(
                    if (state.isBuyingMore) R.string.action_buyMore else R.string.action_buyIn
                )
                is SwapPurpose.BalanceIncrease -> stringResource(R.string.title_amountToBuy)
                is SwapPurpose.BalanceDecrease -> stringResource(R.string.title_amountToSell)
            },
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = {
                if (state.buyProgress.loading) {
                    // swallow
                } else {
                    flowNavigator.back()
                }
            }
        )

        SwapEntryScreenContent(
            viewModel = viewModel,
            largeHeader = purpose is SwapPurpose.Convert || state.isGet,
            accessory = when {
                purpose is SwapPurpose.Convert -> {
                    {
                        ConvertDestinationSelector(
                            destination = state.destinationTokenWithBalance,
                            onClick = {
                                viewModel.dispatchEvent(SwapViewModel.Event.SelectConvertDestination)
                            },
                        )
                    }
                }
                // v2 Get picks what pays for the buy here, so the amount can be capped and the
                // fee priced before confirming. v1 asks on a pushed step afterwards instead.
                state.isGet -> {
                    {
                        BuyFundingSelector(
                            funding = state.fundingTokenWithBalance,
                            onClick = {
                                viewModel.dispatchEvent(SwapViewModel.Event.SelectBuyFundingSource)
                            },
                        )
                    }
                }
                else -> null
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(SwapViewModel.Event.OnPurposeChanged(purpose))
        if (initialAmount != null) {
            viewModel.dispatchEvent(SwapViewModel.Event.OnInitialAmountProvided(initialAmount))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.SelectFundingToken>()
            .map { it.amount }
            .onEach { flowNavigator.navigateTo(SwapStep.TokenSelection(it)) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.SelectBuyFundingSource>()
            .onEach { flowNavigator.navigateTo(SwapStep.FundingSelection) }
            .launchIn(this)
    }

    // v2 Get confirms straight from here, so the receipt push lives here too. Resolving the
    // funding token is async and can fail (stale rates), in which case OnFundingTokenResolved
    // never fires and its alert surfaces on this screen. Gated on isGet so v1 — where the token
    // select screen owns this push and this screen is still in the stack — doesn't double-navigate.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OnFundingTokenResolved>()
            .filter { viewModel.stateFlow.value.isGet }
            .onEach { flowNavigator.navigateTo(SwapStep.BuyReceipt) }
            .launchIn(this)
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
            .filterIsInstance<SwapViewModel.Event.SelectConvertDestination>()
            .onEach {
                flowNavigator.navigateTo(SwapStep.ConvertDestinationSelection)
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.ShowConvertReceipt>()
            .onEach {
                flowNavigator.navigateTo(SwapStep.ConvertReceipt)
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
            .onEach { event ->
                val (phone, email) = event
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

    // Deposit-first "Add Money" via Phantom enters the amount here after connecting;
    // confirming it signs the transaction and advances straight to processing.
    // Replace the whole inner stack so Processing is terminal: the connect prompt and
    // amount-entry steps are cleared, so leaving Processing exits the flow back to the
    // origin (e.g. token info) instead of surfacing the buried Phantom connect prompt.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.PhantomNavigateToProcessing>()
            .onEach {
                flowNavigator.replaceStack(listOf(SwapStep.Processing))
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OtherWalletSelected>()
            .onEach {
                flowNavigator.exitWithResult(SwapResult.OpenDeposit)
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OpenScreen>()
            // OpenScreen here is only ever the buy-shortfall add-money route. Replace the buy flow
            // with it (rather than stacking on top) so that finishing the add-money flow returns
            // the user to the token screen (the origin) instead of the abandoned amount-to-buy step
            // — matching how add-money launched directly from the token screen already behaves.
            .onEach { navigator.replace(it.screen) }
            .launchIn(this)
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
                    viewModel.dispatchEvent(SwapViewModel.Event.DepositSubmitted(completion.orderId))
                    flowNavigator.navigateTo(SwapStep.Processing)
                }
            }
        }
    }
}
