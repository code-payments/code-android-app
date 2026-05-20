package com.flipcash.app.tokens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.tokens.internal.TokenTxProcessingScreen
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.app.tokens.ui.SwapViewModel.Event
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.solana.model.SwapId
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Flow-aware swap processing content, used inside `SwapFlowScreen`.
 */
@Composable
internal fun SwapProcessingScreen() {
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()
    val viewModel = flowSharedViewModel<SwapViewModel>()

    TokenTxProcessingScreen(viewModel = viewModel)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<Event.OnTransactionSuccessful>()
            .onEach {
                flowNavigator.exitWithResult(SwapResult.Success)
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<Event.Exit>()
            .onEach {
                flowNavigator.exitCanceled()
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<Event.PhantomCeremonyFailed>()
            .onEach {
                flowNavigator.exitCanceled()
            }.launchIn(this)
    }

    BackHandler { /* intercept */ }
}

/**
 * Standalone processing screen for OnRamp and external wallet paths that
 * live outside the swap flow.
 */
@Composable
fun TokenTxProcessingScreen(
    swapId: SwapId,
    swapPurpose: SwapPurpose?,
    swapAmount: VerifiedFiat?,
    isFundingShortfall: Boolean = false,
) {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<SwapViewModel>()

    TokenTxProcessingScreen(viewModel = viewModel)

    LaunchedEffect(viewModel, swapId) {
        viewModel.dispatchEvent(Event.OnSwapIdChanged(swapId))
        if (swapPurpose != null) {
            viewModel.dispatchEvent(Event.OnPurposeChanged(swapPurpose))
        }
        if (swapAmount != null) {
            viewModel.dispatchEvent(Event.OnAmountAccepted(swapAmount, swapAmount.localFiat.nativeAmount))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<Event.OnTransactionSuccessful>()
            .onEach {
                if (isFundingShortfall) {
                    navigator.popAll()
                } else {
                    navigator.popUntil { it is AppRoute.Token.Info }
                }
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<Event.Exit>()
            .onEach {
                navigator.popUntil { it is AppRoute.Token.Info }
            }.launchIn(this)
    }

    BackHandler { /* intercept */ }
}
