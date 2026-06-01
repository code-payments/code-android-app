package com.flipcash.app.directsend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.send.SendResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.directsend.internal.SendFlowViewModel
import com.flipcash.app.directsend.internal.screens.ContactListScreen
import com.flipcash.app.directsend.internal.screens.ContactsPermissionGateScreen
import com.flipcash.app.directsend.internal.screens.PhoneGateLandingScreen
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher

@Composable
fun SendFlowScreen(resultStateRegistry: NavResultStateRegistry) {
    val sheetDismiss = LocalBottomSheetDismissDispatcher.current
    val viewModel = hiltViewModel<SendFlowViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    FlowHost<SendStep, SendResult>(
        steps = state.steps,
        resumeAt = 0,
        resultStateRegistry = resultStateRegistry,
        onExit = { reason, _ ->
            when (reason) {
                is FlowExitReason.Completed,
                FlowExitReason.BackedOutOfRoot,
                FlowExitReason.Canceled -> sheetDismiss()
            }
        },
        entryProvider = sendEntryProvider(),
    )
}

private fun sendEntryProvider(): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<SendStep.PhoneGate> {
        SyncStep(it)
        PhoneGateLandingScreen()
    }
    annotatedEntry<SendStep.ContactsGate> {
        SyncStep(it)
        ContactsPermissionGateScreen()
    }
    annotatedEntry<SendStep.ContactList> {
        SyncStep(it)
        ContactListScreen()
    }
}

@Composable
private fun SyncStep(step: SendStep) {
    val viewModel = flowSharedViewModel<SendFlowViewModel>()
    LaunchedEffect(step) {
        viewModel.dispatchEvent(SendFlowViewModel.Event.OnStepChanged(step))
    }
}