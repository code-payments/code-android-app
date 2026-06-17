package com.flipcash.app.directsend.internal.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.analytics.Button
import com.flipcash.app.contacts.device.PickedContactData
import com.flipcash.app.core.send.SendResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.directsend.internal.SendFlowViewModel
import com.flipcash.app.permissions.ContactAccessResult
import com.flipcash.app.permissions.internal.contacts.ContactScreenContent
import com.flipcash.app.permissions.rememberContactAccessHandle
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold
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
            ContactAccessResult.PermanentlyDenied -> {
                // TODO: need something here like notifications
            }
            ContactAccessResult.Canceled -> Unit
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.ContactSyncComplete>()
            .collect { flowNavigator.proceed() }
    }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = "",
                endContent = {
                    AppBarDefaults.Close { flowNavigator.exitCanceled() }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            ContactScreenContent(
                accessHandle = accessHandle,
                isLoading = state.contactSyncState.loading,
                isSuccess = state.contactSyncState.success,
            )
        }
    }
}