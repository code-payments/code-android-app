package com.flipcash.app.tokens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.tokens.internal.TokenTxProcessingScreen
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.app.tokens.ui.SwapViewModel.Event
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
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
