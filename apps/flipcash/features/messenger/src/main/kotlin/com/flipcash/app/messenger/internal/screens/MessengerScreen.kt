package com.flipcash.app.messenger.internal.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.messenger.internal.screens.components.MessageList
import com.flipcash.app.messenger.internal.screens.components.UserControlBottomBar
import com.flipcash.shared.chat.models.ChatAction
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.ScaffoldBarPlacement
import com.getcode.ui.utils.rememberKeyboardController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
internal fun MessengerScreen(viewModel: ChatViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val otherReadPointer by viewModel.otherReadPointer.collectAsStateWithLifecycle(null)
    val navigator = LocalCodeNavigator.current

    val hazeState = rememberHazeState()
    val keyboard = rememberKeyboardController()

    val chatActionHandler = { action: ChatAction ->
        when (action) {
            is ChatAction.AdvanceReadPointer -> {
                viewModel.dispatchEvent(ChatViewModel.Event.AdvanceReadPointer(action.messageId))
            }

            ChatAction.RefreshContact -> {
                viewModel.dispatchEvent(ChatViewModel.Event.RefreshContact)
            }

            is ChatAction.RetryMessage -> {
                keyboard.hideIfVisible {
                    viewModel.dispatchEvent(
                        ChatViewModel.Event.RetryMessage(
                            action.bubble.pendingClientIdHex,
                            action.bubble.content
                        )
                    )
                }
            }

            is ChatAction.ViewToken -> {
                keyboard.hideIfVisible {
                    viewModel.dispatchEvent(
                        ChatViewModel.Event.OpenScreen(AppRoute.Token.Info(action.mint))
                    )
                }
            }

            is ChatAction.ToggleSelection -> {
                viewModel.dispatchEvent(
                    ChatViewModel.Event.ToggleMessageSelection(action.bubble)
                )
            }

            ChatAction.ClearSelection -> {
                viewModel.dispatchEvent(ChatViewModel.Event.ClearMessageSelection)
            }

            ChatAction.CancelEdit -> {
                viewModel.dispatchEvent(ChatViewModel.Event.CancelEdit)
            }

            is ChatAction.ViewProfile -> {
                // The triggers (top-bar tap, contact-card chevron) are only clickable for tip DMs
                // (see State.canViewProfile), so no gating is needed here.
                state.participant?.let {
                    keyboard.hideIfVisible {
                        navigator.push(ChatStep.Profile(it))
                    }
                }
            }
        }

        Unit
    }

    // Back unwinds the message actions before it leaves the conversation, innermost first: an edit
    // in progress, then the selection bar.
    BackHandler(enabled = state.editing != null) {
        viewModel.dispatchEvent(ChatViewModel.Event.CancelEdit)
    }
    BackHandler(enabled = state.editing == null && state.selection != null) {
        viewModel.dispatchEvent(ChatViewModel.Event.ClearMessageSelection)
    }

    CodeScaffold(
        // The input bar rides the keyboard; the message list is inset by it either way.
        modifier = Modifier.imePadding(),
        // The list runs the full height and passes under both bars, each of which fades it out
        // against the background at its own edge.
        barPlacement = ScaffoldBarPlacement.Overlay,
        topBar = { ChatTopBar(navigator, state, chatActionHandler, viewModel::dispatchEvent) },
        bottomBar = {
            UserControlBottomBar(
                state = state,
                hazeState = hazeState,
                dispatch = viewModel::dispatchEvent,
            )
        },
    ) { overlapPadding ->
        MessageList(
            modifier = Modifier
                .fillMaxSize()
                .testTag("chat_message_list")
                .hazeSource(hazeState),
            state = state,
            contentPadding = overlapPadding,
            messages = messages,
            separatorConfig = state.separatorConfig,
            otherReadPointer = otherReadPointer,
            onAction = chatActionHandler,
            canViewProfile = state.canViewProfile,
        )
    }
}
