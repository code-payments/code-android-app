package com.flipcash.app.messenger

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.MessengerScreen
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.flowAnnotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.results.NavResultStateRegistry
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

@Composable
fun ChatFlowScreen(
    route: AppRoute.Messaging.Chat,
    resultStateRegistry: NavResultStateRegistry,
) {
    val navigator = LocalCodeNavigator.current

    FlowHost<ChatStep, Parcelable>(
        initialStack = route.rememberInitialStack(),
        resultStateRegistry = resultStateRegistry,
        onExit = { _, _ -> navigator.pop() },
        entryProvider = chatEntryProvider(route.identifier),
    )
}

@Composable
private fun chatEntryProvider(
    identifier: AppRoute.ChatIdentifier,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<ChatStep.Conversation> {
        FlowConversationScreen(identifier)
    }
    flowAnnotatedEntry<ChatStep.AmountEntry> {
        FlowAmountEntryScreen()
    }
}

@Composable
private fun FlowConversationScreen(identifier: AppRoute.ChatIdentifier) {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()

    LaunchedEffect(viewModel, identifier) {
        viewModel.dispatchEvent(ChatViewModel.Event.OnChatOpened(identifier))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.NavigateToAmountEntry>()
            .collect {
                flowNavigator.navigateTo(ChatStep.AmountEntry)
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.NavigateToUsdfDepositOption>()
            .map { it.route }
            .collect { route ->
                flowNavigator.navigate(route)
            }
    }

    MessengerScreen(viewModel)
}

@Composable
private fun FlowAmountEntryScreen() {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()

    ChatAmountEntryContent(
        amountDelegate = viewModel.amountDelegate,
        resolveState = state.resolveState,
        chattingWithName = state.chattingWith?.displayName,
        token = state.token,
        eventFlow = viewModel.eventFlow,
        onConfirm = { viewModel.dispatchEvent(ChatViewModel.Event.OnConfirmRequested) },
        onSendComplete = { flowNavigator.back() },
    )
}
