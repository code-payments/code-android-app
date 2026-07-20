package com.flipcash.app.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.toast.LocalToastController
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.SwapViewModel
import com.getcode.opencode.model.financial.Fiat
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.deliverFlowResult
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun SwapFlowScreen(
    route: AppRoute.Token.Swap,
    resultStateRegistry: NavResultStateRegistry,
) {
    val outerNavigator = LocalCodeNavigator.current
    val toastController = LocalToastController.current
    val initialStack = route.rememberInitialStack<SwapStep>()

    FlowHost<SwapStep, SwapResult>(
        initialStack = initialStack,
        resultStateRegistry = resultStateRegistry,
        onExit = { reason, _ ->
            val result = when (reason) {
                is FlowExitReason.Completed -> reason.result
                FlowExitReason.Canceled,
                FlowExitReason.BackedOutOfRoot -> SwapResult.Canceled
            }
            outerNavigator.deliverFlowResult(
                route = route,
                value = NavResultOrCanceled.ReturnValue(result),
            )
            when (result) {
                is SwapResult.Success -> {
                    if (route.shortfall != null || route.popToRoot) {
                        if (result.amount > Fiat.Zero) {
                            toastController.showToast(result.amount, isDeposit = true)
                        }
                        outerNavigator.popAll()
                    }
                    // pop() at root triggers onRootReached → sheet dismiss
                    outerNavigator.pop()
                }
                SwapResult.OpenDeposit,
                SwapResult.Canceled -> outerNavigator.pop()
            }
        },
        entryProvider = swapEntryProvider(route),
    )
}

private fun swapEntryProvider(
    route: AppRoute.Token.Swap,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    // When the flow begins directly at the connect screen (deposit-first "Add Money"
    // via Phantom), there is no preceding amount-entry step. Carry the purpose through
    // so that, once connected, we can route into amount entry instead of the
    // transaction-confirmation gate.
    val depositFirstPurpose = (route.purpose as? SwapPurpose.Buy)
        ?.takeIf { it.fundingSource == FundingSource.Phantom }

    // SwapEntryScreen reads only the inner LocalCodeNavigator; its cross-boundary result nav
    // resolves to the app navigator via the dispatcher, so a plain annotatedEntry is correct.
    annotatedEntry<SwapStep.Entry> { step ->
        SwapEntryScreen(step.purpose, step.initialAmount)
    }
    annotatedEntry<SwapStep.TokenSelection> { key ->
        SwapPurchaseTokenSelectScreen(route.purpose.mint, key.amount)
    }
    annotatedEntry<SwapStep.BuyReceipt> {
        BuyReceiptScreen()
    }
    annotatedEntry<SwapStep.SellReceipt> { SellReceiptScreen() }
    annotatedEntry<SwapStep.PhantomConnect> {
        PhantomConnectConfirmationScreen(depositFirstPurpose = depositFirstPurpose)
    }
    annotatedEntry<SwapStep.PhantomConfirmTransaction> {
        PhantomTransactionConfirmationScreen()
    }
    annotatedEntry<SwapStep.Processing> {
        SwapProcessingScreen()
    }
}

@Composable
private fun SwapPurchaseTokenSelectScreen(targetMint: Mint, amount: Fiat) {
    val selectionViewModel = hiltViewModel<SelectTokenViewModel>()
    val viewModel = flowSharedViewModel<SwapViewModel>()
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()

    TokenSelectScreen(TokenPurpose.Swap(targetMint, amount))

    LaunchedEffect(selectionViewModel) {
        selectionViewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
            .filter { it.fromUser }
            .map { it.mint }
            .onEach {
                viewModel.dispatchEvent(SwapViewModel.Event.OnFundingTokenSelected(it))
            }
            .launchIn(this)
    }

    // Resolving the funding token is async (metadata + rate/reserve lookup) and can fail
    // (e.g. stale rates), in which case OnFundingTokenResolved never fires. Navigate to the
    // receipt only once it actually resolves, so we never land on a receipt with no funding
    // token — a failed resolve surfaces its alert here on the select screen instead.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OnFundingTokenResolved>()
            .onEach { flowNavigator.navigateTo(SwapStep.BuyReceipt) }
            .launchIn(this)
    }
}
