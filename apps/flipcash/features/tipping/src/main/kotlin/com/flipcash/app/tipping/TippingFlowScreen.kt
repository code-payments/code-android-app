package com.flipcash.app.tipping

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tipping.TipResult
import com.flipcash.app.core.tipping.TipStep
import com.flipcash.app.tipping.internal.TipFlowViewModel
import com.flipcash.app.tipping.internal.screens.TipCardScreen
import com.flipcash.app.tipping.internal.screens.TipInfoScreen
import com.flipcash.app.tipping.internal.screens.TipsScreen
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.ui.utils.rememberKeyboardController

@Composable
fun TippingFlowScreen(
    route: AppRoute.Sheets.Tips,
    resultStateRegistry: NavResultStateRegistry,
) {
    val sheetDismiss = LocalBottomSheetDismissDispatcher.current
    val viewModel = hiltViewModel<TipFlowViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val keyboard = rememberKeyboardController()

    // How the flow was entered gates the step list (post-setup handoff → TipCard alone).
    LaunchedEffect(route.resumed) {
        viewModel.dispatchEvent(TipFlowViewModel.Event.OnResumed(route.resumed))
    }

    FlowHost<TipStep, TipResult>(
        steps = state.steps,
        resumeAt = 0,
        resultStateRegistry = resultStateRegistry,
        // Proceeding off the end (TipCard's Done, or backing out at root) completes the flow.
        completedResult = TipResult.Success,
        onExit = { reason, _ ->
            when (reason) {
                is FlowExitReason.Completed,
                FlowExitReason.BackedOutOfRoot,
                FlowExitReason.Canceled -> keyboard.hideIfVisible { sheetDismiss() }
            }
        },
        entryProvider = tippingEntryProvider(),
    )
}

private fun tippingEntryProvider(): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<TipStep.Intro> { SyncStep(it); TipInfoScreen() }
    annotatedEntry<TipStep.TipCard> { SyncStep(it); TipCardScreen() }
    annotatedEntry<TipStep.Tips> { SyncStep(it); TipsScreen() }
}

@Composable
private fun SyncStep(step: TipStep) {
    val viewModel = flowSharedViewModel<TipFlowViewModel>()
    LaunchedEffect(step) {
        viewModel.dispatchEvent(TipFlowViewModel.Event.OnStepChanged(step))
    }
}
