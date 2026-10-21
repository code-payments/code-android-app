package com.flipcash.app.tokens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.tokens.ui.SwapViewModel
import com.getcode.navigation.flow.FlowNavigator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Wires a screen that submits a swap and completes it in place (buy, sell, convert):
 *  - [SwapViewModel.Event.OnTransactionSuccessful] exits the flow, the same exit Processing's OK does.
 *  - [SwapViewModel.Event.ProcessingTakingLonger] hands off to Processing once the fast window lapses.
 *  - back is swallowed while the confirm button is spinning.
 */
@Composable
internal fun SwapCompletionEffects(
    viewModel: SwapViewModel,
    flowNavigator: FlowNavigator<SwapStep, SwapResult>,
    isSubmitting: Boolean,
) {
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OnTransactionSuccessful>()
            .onEach {
                val amount = viewModel.stateFlow.value.enteredAmount
                flowNavigator.exitWithResult(SwapResult.Success(amount))
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.ProcessingTakingLonger>()
            .onEach { flowNavigator.navigateTo(SwapStep.Processing) }
            .launchIn(this)
    }

    BackHandler(enabled = isSubmitting) { /* swallow */ }
}
