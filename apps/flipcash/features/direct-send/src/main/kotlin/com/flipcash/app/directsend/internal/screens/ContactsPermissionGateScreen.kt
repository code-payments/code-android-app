package com.flipcash.app.directsend.internal.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.analytics.Button
import com.flipcash.app.core.send.SendResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.directsend.internal.ContactListItem
import com.flipcash.app.directsend.internal.SendFlowViewModel
import com.flipcash.app.permissions.ContactAccessResult
import com.flipcash.app.permissions.internal.contacts.ContactRationaleContent
import com.flipcash.app.permissions.internal.contacts.ContactScreenContent
import com.flipcash.app.permissions.rememberContactAccessHandle
import com.flipcash.features.directsend.R
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.permissions.PermissionResult
import kotlinx.coroutines.flow.filterIsInstance

@Composable
internal fun ContactsPermissionGateScreen() {
    val flowNavigator = rememberFlowNavigator<SendStep, SendResult>()
    val viewModel = flowSharedViewModel<SendFlowViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val analytics = LocalAnalytics.current

    val accessHandle = rememberContactAccessHandle(
        isPickerMode = state.isPickerMode,
    ) { result ->
        when (result) {
            ContactAccessResult.Granted -> {
                analytics.action(Button.AllowContacts)
                viewModel.dispatchEvent(SendFlowViewModel.Event.ContactsGranted)
            }
            is ContactAccessResult.Picked -> {
                analytics.action(Button.AllowContacts)
                viewModel.dispatchEvent(SendFlowViewModel.Event.ContactsPicked(result.contacts))
            }
            ContactAccessResult.Denied,
            ContactAccessResult.PermanentlyDenied -> Unit
            ContactAccessResult.Canceled -> Unit
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.ContactSyncComplete>()
            .collect { flowNavigator.proceed() }
    }

    // React to permission status changes when returning from Settings
    LaunchedEffect(accessHandle.permissionStatus) {
        when (accessHandle.permissionStatus) {
            PermissionResult.Granted -> {
                viewModel.dispatchEvent(SendFlowViewModel.Event.ContactsGranted)
            }
            PermissionResult.Denied,
            PermissionResult.PermanentlyDenied -> {
                viewModel.dispatchEvent(SendFlowViewModel.Event.ContactsRevoked)
            }
            PermissionResult.NotRequested -> Unit
        }
    }

    val permissionDenied = accessHandle.permissionStatus == PermissionResult.Denied ||
        accessHandle.permissionStatus == PermissionResult.PermanentlyDenied

    val showRationale = permissionDenied && !state.isPickerMode

    // If this step has been removed from the flow (e.g. chats arrived while we're
    // on this screen, or on re-open when chats already exist), advance immediately.
    // FlowNavigator.proceed() handles removed steps by replacing with the first remaining step.
    //
    // Exception: while a sync is actively running (the user just granted access),
    // the device contacts land first and remove this gate from the steps, but the
    // on-Flipcash contacts are fetched in a second pass. If we advanced now, the list
    // would render with only the "not on Flipcash" section, then prepend the
    // on-Flipcash contacts above the viewport once they arrive — pushing them
    // offscreen. Let ContactSyncComplete drive navigation instead so the list is
    // fully formed before it's shown.
    val gateStillInSteps = state.steps.any { it is SendStep.ContactsGate }
    if (!gateStillInSteps && !state.contactSyncState.loading) {
        LaunchedEffect(Unit) { flowNavigator.proceed() }
        return
    }

    val hasChats = state.listItems.any { it is ContactListItem.ContactRow && it.chatId != null }

    // If denied but user has existing chats, skip the gate entirely
    if (showRationale && hasChats) {
        LaunchedEffect(Unit) { flowNavigator.proceed() }
        return
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_send),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = {
                    AppBarDefaults.Close { flowNavigator.exitCanceled() }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (showRationale) {
                ContactRationaleContent(simplified = true)
            } else {
                ContactScreenContent(
                    accessHandle = accessHandle,
                    simplified = true,
                    isLoading = state.contactSyncState.loading,
                    isSuccess = state.contactSyncState.success,
                )
            }
        }
    }
}
