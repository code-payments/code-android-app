package com.flipcash.app.tipping

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.NewGroupStep
import com.flipcash.app.tipping.internal.CreateGroupViewModel
import com.flipcash.app.tipping.internal.screens.NewGroupCustomAmountSheet
import com.flipcash.app.tipping.internal.screens.NewGroupFormScreen
import com.flipcash.app.tipping.internal.screens.SelectGroupCurrencySheet
import com.flipcash.services.models.chat.ChatId
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Creating a public group — section 10153:22901, from the empty form to the created chat.
 *
 * A flow, and non-linear: the currency sheet and the custom-amount keypad are edits to the form's
 * draft that return to it. So the steps navigate themselves rather than advancing through a list,
 * and the shared [CreateGroupViewModel] — scoped to the flow, not to any one step — is what they
 * are all editing. Holding the draft there is also what gives one create attempt's idempotency key
 * a lifetime long enough to survive a retry.
 *
 * The flow ends at `StartChat` succeeding. Handing out the invite link is offered from inside the
 * group ([com.flipcash.app.core.chat.ChatStep.InviteToGroup]) rather than stacked on the form, so
 * creating a group and inviting people to it are separate decisions.
 */
@Composable
fun NewGroupFlowScreen(
    route: AppRoute.Messaging.NewGroup,
    resultStateRegistry: NavResultStateRegistry,
) {
    val navigator = LocalCodeNavigator.current
    val keyboard = rememberKeyboardController()

    // What the flow produced, held out here rather than read from the draft on the way out: the
    // shared view model is scoped *inside* the host, so `onExit` — which is where leaving has to be
    // acted on, because a finished create and an abandoned one are the same departure — has no way
    // to reach it.
    var createdChat by remember { mutableStateOf<ChatId?>(null) }

    FlowHost<NewGroupStep, Parcelable>(
        initialStack = route.rememberInitialStack(),
        resultStateRegistry = resultStateRegistry,
        // The name field owns the keyboard for most of the flow, so the same rule the chat flow
        // follows applies: put it away before the screen behind this one is uncovered.
        onExit = { _, _ ->
            keyboard.hideIfVisible {
                val chatId = createdChat
                if (chatId == null) {
                    navigator.pop()
                } else {
                    // Leaving a created group opens it rather than returning to the Chats list.
                    // The chat is in that list already — `create` persisted it — but the user's last
                    // action was making this group, so that is what they land on. Popping by route
                    // type, as FindByUsername does, because the chooser that opened this flow is
                    // entry UI for the same chat and should not sit between it and the list.
                    navigator.popUntil {
                        it !is AppRoute.Messaging.NewGroup && it !is AppRoute.Messaging.NewChat
                    }
                    navigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chatId)))
                }
            }
        },
        entryProvider = newGroupEntryProvider(onChatCreated = { createdChat = it }),
        // SelectCurrency and CustomAmount are Sheets; without the sheet strategy they would fall
        // through to SinglePane and cover the form instead of sitting over it. Neither returns a
        // result through the store — both write straight to the shared draft — so the strategy's
        // dismiss-delivers-Canceled path has nothing to address, hence the null key.
        sceneStrategies = listOf(
            ModalBottomSheetSceneStrategy(navigator.resultStore) { null },
            SinglePaneSceneStrategy(),
        ),
    )
}

@Composable
private fun newGroupEntryProvider(
    onChatCreated: (ChatId) -> Unit,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<NewGroupStep.Form> {
        FlowNewGroupFormScreen(onChatCreated = onChatCreated)
    }
    annotatedEntry<NewGroupStep.SelectCurrency> {
        SelectGroupCurrencySheet(flowSharedViewModel<CreateGroupViewModel>())
    }
    annotatedEntry<NewGroupStep.CustomAmount> {
        NewGroupCustomAmountSheet(flowSharedViewModel<CreateGroupViewModel>())
    }
}

@Composable
private fun FlowNewGroupFormScreen(onChatCreated: (ChatId) -> Unit) {
    val viewModel = flowSharedViewModel<CreateGroupViewModel>()
    val flowNavigator = rememberFlowNavigator<NewGroupStep, Parcelable>()

    NewGroupFormScreen(viewModel)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CreateGroupViewModel.Event.ChatCreated>()
            .onEach { event ->
                onChatCreated(event.chat.chatId)
                // The draft has become a chat, so the flow is over — leaving is what opens it, in
                // the host's `onExit` above. Exiting rather than popping the form, because the form
                // is the flow's root and there is nothing behind it to return to.
                flowNavigator.exitCanceled()
            }
            .launchIn(this)
    }
}
