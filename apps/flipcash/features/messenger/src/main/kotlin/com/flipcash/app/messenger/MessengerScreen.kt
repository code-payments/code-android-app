package com.flipcash.app.messenger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.app.core.AppRoute
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.MessengerScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.flowScopedViewModel
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

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.NavigateToUsdfDepositOption>()
            .map { it.route }
            .collect { route ->
                navigator.push(route)
            }
    }

    MessengerScreen(viewModel)
}
