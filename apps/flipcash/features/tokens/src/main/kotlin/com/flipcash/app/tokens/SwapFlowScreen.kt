package com.flipcash.app.tokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.toast.LocalToastController
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenListPresentation
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
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.flipcash.features.tokens.R
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
        // The currency pickers are overlay scenes, so amount entry stays composed beneath them —
        // picking a currency never re-runs the entry screen's effects.
        sceneStrategies = listOf(
            ModalBottomSheetSceneStrategy(outerNavigator.resultStore) { null },
            SinglePaneSceneStrategy(),
        ),
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
    // Explicitly tagged: the derived id would be `entry_screen`, which says nothing about which
    // flow it belongs to. `maestro/buy.yaml` / `maestro/sell.yaml` anchor on `swap_screen`.
    annotatedEntry<SwapStep.Entry>(testTag = "swap_screen") { step ->
        SwapEntryScreen(step.purpose, step.initialAmount)
    }
    annotatedEntry<SwapStep.TokenSelection> { key ->
        SwapPurchaseTokenSelectScreen(route.purpose.mint, key.amount)
    }
    annotatedEntry<SwapStep.BuyReceipt> {
        BuyReceiptScreen()
    }
    annotatedEntry<SwapStep.SellReceipt> { SellReceiptScreen() }
    annotatedEntry<SwapStep.ConvertDestinationSelection> {
        ConvertDestinationSelectScreen()
    }
    annotatedEntry<SwapStep.FundingSelection> {
        BuyFundingSelectScreen()
    }
    annotatedEntry<SwapStep.ConvertReceipt> { ConvertReceiptScreen() }
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

/**
 * Destination picker for a conversion. Selecting a currency updates the in-flight purpose and pops
 * straight back to amount entry — nothing else in the flow changes, so there's no resolve to await.
 */
@Composable
private fun ConvertDestinationSelectScreen() {
    val selectionViewModel = hiltViewModel<SelectTokenViewModel>()
    val viewModel = flowSharedViewModel<SwapViewModel>()
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()
    val purpose = viewModel.stateFlow.collectAsStateWithLifecycle().value.purpose

    val convert = purpose as? SwapPurpose.Convert ?: return

    // Height is the sheet's business, not the content's: the sheet hands down its current detent
    // height and the list simply fills it. Re-stating a fraction of the screen here would fight
    // that — it capped the list short of the sheet's own bottom edge, stranding the list's edge
    // fade above it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Sheets stop short of the system bars, so pad for them here.
            .navigationBarsPadding(),
    ) {
        // A sheet has no app bar: the title sits flush-left above the list, and the sheet's own
        // scrim/drag handles dismissal.
        Text(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(
                    top = CodeTheme.dimens.staticGrid.x7,
                    bottom = CodeTheme.dimens.staticGrid.x5,
                ),
            text = stringResource(R.string.title_selectCurrency),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )

        TokenSelectScreen(
            purpose = TokenPurpose.ConvertDestination(convert.mint, convert.destinationMint),
            showTopBar = false,
            presentation = TokenListPresentation.Sheet,
        )
    }

    LaunchedEffect(selectionViewModel) {
        selectionViewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
            .filter { it.fromUser }
            .map { it.mint }
            .onEach {
                viewModel.dispatchEvent(SwapViewModel.Event.OnDestinationSelected(it))
                flowNavigator.back()
            }
            .launchIn(this)
    }
}

/**
 * Payment-source picker for a v2 Get. Selecting a currency re-points the entry cap and pops back to
 * amount entry — unlike [SwapPurchaseTokenSelectScreen], which prices the buy and pushes a receipt.
 */
@Composable
private fun BuyFundingSelectScreen() {
    val selectionViewModel = hiltViewModel<SelectTokenViewModel>()
    val viewModel = flowSharedViewModel<SwapViewModel>()
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()
    val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

    val buy = state.purpose as? SwapPurpose.Buy ?: return
    val current = state.fundingMint ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Text(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(
                    top = CodeTheme.dimens.staticGrid.x7,
                    bottom = CodeTheme.dimens.staticGrid.x5,
                ),
            text = stringResource(R.string.title_selectCurrency),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )

        TokenSelectScreen(
            purpose = TokenPurpose.BuyFunding(target = buy.mint, current = current),
            showTopBar = false,
            presentation = TokenListPresentation.Sheet,
        )
    }

    LaunchedEffect(selectionViewModel) {
        selectionViewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
            .filter { it.fromUser }
            .map { it.mint }
            .onEach {
                viewModel.dispatchEvent(SwapViewModel.Event.OnFundingSourceSelected(it))
                flowNavigator.back()
            }
            .launchIn(this)
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
