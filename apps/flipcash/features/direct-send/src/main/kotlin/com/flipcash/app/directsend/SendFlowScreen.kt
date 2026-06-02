package com.flipcash.app.directsend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.send.SendResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.directsend.internal.SendFlowViewModel
import com.flipcash.app.directsend.internal.screens.AmountEntryResult
import com.flipcash.app.directsend.internal.screens.AmountEntryScreen
import com.flipcash.app.directsend.internal.screens.ContactListScreen
import com.flipcash.app.directsend.internal.screens.ContactsPermissionGateScreen
import com.flipcash.app.directsend.internal.screens.PhoneGateLandingScreen
import com.flipcash.features.directsend.R
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.LocalOuterCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.LocalResources
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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

@Composable
private fun sendEntryProvider(): (NavKey) -> NavEntry<NavKey> {
    return entryProvider {
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
        annotatedEntry<SendStep.AmountEntry> { step ->
            SyncStep(step)
            SendAmountEntryScreen()
        }
    }
}

@Composable
private fun SendAmountEntryScreen() {
    val flowNavigator = rememberFlowNavigator<SendStep, SendResult>()
    val sharedVm = flowSharedViewModel<SendFlowViewModel>()
    val sharedState by sharedVm.stateFlow.collectAsStateWithLifecycle()
    val resources = LocalResources.current

    var contact by remember {
        mutableStateOf<DeviceContact?>(null)
    }

    var resolvedAuthority by remember {
        mutableStateOf<PublicKey?>(null)
    }

    LaunchedEffect(Unit) {
        sharedVm.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.ResolveCompleted>()
            .onEach {
                contact = it.contact
                resolvedAuthority = it.authority
            }
            .launchIn(this)
    }

    LaunchedEffect(sharedVm) {
        sharedVm.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.SendComplete>()
            .onEach { event ->
                BottomBarManager.showInfo(
                    title = resources.getString(R.string.prompt_title_fundsSentToContact),
                    message = resources.getString(
                        R.string.prompt_description_fundsSentToContact,
                        event.amount.formatted(rule = Fiat.FormattingRule.Truncated),
                        contact?.displayName ?: "your selected recipient"
                    ),
                    onDismiss = { flowNavigator.back() }
                )
            }.launchIn(this)
    }

    AmountEntryScreen(
        title = { token ->
            TokenSelectionPill(token) {
                flowNavigator.navigate(
                    AppRoute.Sheets.TokenSelection(TokenPurpose.Select)
                )
            }
        },
        // region lives outside the flow
        // flow replaces LocalCodeNavigator for the flow as the "inner" navigator
        navigator = LocalOuterCodeNavigator.current,
        canChangeCurrency = true,
        confirmationStyle = ConfirmationStyle.Slide,
        confirmationState = sharedState.sendProgress,
        onResult = { result ->
            when (result) {
                AmountEntryResult.Cancelled -> flowNavigator.back()
                is AmountEntryResult.Confirmed -> {
                    val destination = resolvedAuthority
                    if (destination == null) {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_contactNotOnFlipcash),
                            message = resources.getString(R.string.error_description_contactNotOnFlipcash),
                            onDismiss = { flowNavigator.back() }
                        )
                    } else {
                        sharedVm.dispatchEvent(
                            SendFlowViewModel.Event.OnSendRequested(
                                amount = result.amount,
                                token = result.token,
                                destinationOwner = destination,
                            )
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SyncStep(step: SendStep) {
    val viewModel = flowSharedViewModel<SendFlowViewModel>()
    LaunchedEffect(step) {
        viewModel.dispatchEvent(SendFlowViewModel.Event.OnStepChanged(step))
    }
}