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
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatSendResult
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.MessengerScreen
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.results.navigateForResult
import com.getcode.navigation.results.resultBackNavigator
import com.getcode.navigation.scenes.LocalSheetNavigator
import kotlinx.coroutines.flow.filterIsInstance

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
    identifier: ChatIdentifier,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<ChatStep.Conversation> {
        FlowConversationScreen(identifier)
    }
    annotatedEntry<ChatStep.AmountEntry> {
        FlowAmountEntryScreen()
    }
}

@Composable
private fun FlowConversationScreen(identifier: ChatIdentifier) {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val navigator = LocalCodeNavigator.current
    // The sheet-owning (root) navigator — the one whose back stack holds this chat's Main.Sheet and
    // whose pendingSheetDismiss the dismiss animation observes. openAsSheet is stack-local (it
    // inspects/mutates this navigator's own back stack), so it can't ride the type dispatcher and
    // must target the sheet navigator explicitly.
    val sheetNavigator = LocalSheetNavigator.current

    LaunchedEffect(viewModel, identifier) {
        viewModel.dispatchEvent(ChatViewModel.Event.OnChatOpened(identifier))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.NavigateToAmountEntry>()
            .collect {
                // ChatStep.AmountEntry is a FlowStep -> the dispatcher keeps this push on the inner
                // flow stack and registers the result callback on the inner store (intra-flow).
                navigator.navigateForResult<ChatSendResult>(ChatStep.AmountEntry) { result ->
                    if (result is NavResultOrCanceled.ReturnValue) {
                        viewModel.dispatchEvent(ChatViewModel.Event.OnStartMessageInput)
                    }
                }
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.OpenScreen>()
            .collect { (route, asSheet) ->
                if (asSheet) {
                    // Dismiss this chat sheet and open [route] as a fresh sheet. openAsSheet on the
                    // sheet-owning navigator animates the current sheet closed (via pendingSheetDismiss)
                    // before opening the new one.
                    sheetNavigator?.openAsSheet(route)
                } else {
                    // A full AppRoute (never a ChatStep) -> the dispatcher bubbles it up the parent
                    // chain to the outer app nav host, the same destination as the old
                    // outerNavigator.navigate(route).
                    navigator.navigate(route)
                }
            }
    }

    MessengerScreen(viewModel)
}

@Composable
private fun FlowAmountEntryScreen() {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val resultBack = resultBackNavigator<ChatSendResult>()

    // No re-shadow: ChatAmountEntryContent reads the inner LocalCodeNavigator, and its
    // navigator.push(AppRoute.Main.RegionSelection) / push(AppRoute.Sheets.TokenSelection)
    // bubble to the app navigator through the dispatcher.
    ChatAmountEntryContent(
        amountDelegate = viewModel.amountDelegate,
        resolveState = state.resolveState,
        token = state.token,
        eventFlow = viewModel.eventFlow,
        onConfirm = { viewModel.dispatchEvent(ChatViewModel.Event.OnConfirmRequested) },
        onSendComplete = { resultBack.returnValue(ChatSendResult) }, // intra-flow result -> Conversation
        onExit = { navigator.navigateBack() },                       // pop the AmountEntry step
    )
}
