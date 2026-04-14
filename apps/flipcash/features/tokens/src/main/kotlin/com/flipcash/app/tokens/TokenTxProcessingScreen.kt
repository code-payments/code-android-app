package com.flipcash.app.tokens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.onramp.ExternalWalletOnRampState
import com.flipcash.app.onramp.LocalExternalWalletOnRampController
import com.flipcash.app.tokens.internal.TokenTxProcessingScreen
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.app.tokens.ui.SwapViewModel.Event
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Flow-aware swap processing content, used inside `SwapFlowScreen`.
 */
@Composable
internal fun SwapProcessingContent(
    swapId: SwapId,
    awaitExternalWallet: Boolean = false,
) {
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()
    val viewModel = flowSharedViewModel<SwapViewModel>()

    var awaitingWallet by remember { mutableStateOf(awaitExternalWallet) }

    TokenTxProcessingScreen(
        viewModel = viewModel,
        processingProgressOverride = if (awaitingWallet) LoadingSuccessState(loading = true) else null,
    )

    if (awaitExternalWallet) {
        val controller = LocalExternalWalletOnRampController.current
        LaunchedEffect(viewModel, swapId) {
            val terminalState = controller.state
                .firstOrNull { it is ExternalWalletOnRampState.Transacted || it is ExternalWalletOnRampState.Idle }

            if (terminalState !is ExternalWalletOnRampState.Transacted) {
                flowNavigator.back()
                return@LaunchedEffect
            }

            controller.reset()
            viewModel.dispatchEvent(Event.OnSwapIdChanged(swapId))

            snapshotFlow { viewModel.stateFlow.value.processingProgress }
                .firstOrNull { it.loading }

            awaitingWallet = false
        }
    } else {
        LaunchedEffect(viewModel, swapId) {
            viewModel.dispatchEvent(Event.OnSwapIdChanged(swapId))
        }
    }

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

    BackHandler { /* intercept */ }
}

/**
 * Standalone processing screen for OnRamp and external wallet paths that
 * live outside the swap flow.
 */
@Composable
fun TokenTxProcessingScreen(
    swapId: SwapId,
    awaitExternalWallet: Boolean = false,
    isFundingShortfall: Boolean = false,
) {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<SwapViewModel>()

    var awaitingWallet by remember { mutableStateOf(awaitExternalWallet) }

    TokenTxProcessingScreen(
        viewModel = viewModel,
        processingProgressOverride = if (awaitingWallet) LoadingSuccessState(loading = true) else null,
    )

    if (awaitExternalWallet) {
        val controller = LocalExternalWalletOnRampController.current
        LaunchedEffect(viewModel, swapId) {
            val terminalState = controller.state
                .firstOrNull { it is ExternalWalletOnRampState.Transacted || it is ExternalWalletOnRampState.Idle }

            if (terminalState !is ExternalWalletOnRampState.Transacted) {
                navigator.pop()
                return@LaunchedEffect
            }

            controller.reset()
            viewModel.dispatchEvent(Event.OnSwapIdChanged(swapId))

            snapshotFlow { viewModel.stateFlow.value.processingProgress }
                .firstOrNull { it.loading }

            awaitingWallet = false
        }
    } else {
        LaunchedEffect(viewModel, swapId) {
            viewModel.dispatchEvent(Event.OnSwapIdChanged(swapId))
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
