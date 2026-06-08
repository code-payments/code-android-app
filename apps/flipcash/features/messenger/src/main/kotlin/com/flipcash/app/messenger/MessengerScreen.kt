package com.flipcash.app.messenger

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.core.AppRoute
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.MessengerScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.flowScopedViewModel
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

@Composable
fun MessengerScreen(e164: String, displayName: String) {
    val viewModel = flowScopedViewModel<ChatViewModel>(e164)
    val navigator = LocalCodeNavigator.current

    LaunchedEffect(Unit) {
        viewModel.dispatchEvent(ChatViewModel.Event.OnChatOpened(e164, displayName))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.NavigateToAmountEntry>()
            .map { it.contact }
            .collect { contact ->
                navigator.push(AppRoute.Messaging.AmountEntry(
                    e164 = contact.e164,
                    displayName = contact.displayName,
                ))
            }
    }

    MessengerScreen(viewModel)
}
