package com.flipcash.app.messenger.internal.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.messenger.internal.screens.components.ChatTopEdge
import com.flipcash.app.messenger.internal.screens.components.ChatTopEdge.softTopEdge
import com.flipcash.app.messenger.internal.screens.components.MessageList
import com.flipcash.app.messenger.internal.screens.components.UserControlBottomBar
import com.getcode.ui.components.BlurredContent
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
    // Measured by the bar and read by the transcript: the blur has to cover the bar's own
    // height, which the selection and editing modes change.
    var barHeight by remember { mutableStateOf(0.dp) }
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
                        ChatViewModel.Event.OpenScreen(
                            // A drill-in from the transcript, so push it: the fade-in-place
                            // expand is the wallet card growing into its own detail, and there
                            // is no card here for it to grow from.
                            AppRoute.Token.Info(
                                action.mint,
                                asPush = true,
                                returnAfterBuy = action.returnAfterBuy,
                            )
                        )
                    )
                }
            }

            is ChatAction.OpenGroup -> {
                // An invite to the chat already on screen has nowhere to go.
                if (action.chatId != state.chatId) {
                    keyboard.hideIfVisible {
                        viewModel.dispatchEvent(
                            ChatViewModel.Event.OpenScreen(
                                // Pushed, so Back returns to this chat. The pushed screen gates
                                // itself: a non-member sees the join or the buy there.
                                AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(action.chatId))
                            )
                        )
                    }
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

            // Both reply entry points land on one event so the citation is resolved in one place;
            // turning a bubble into a quote needs a database read.
            is ChatAction.ReplyTo -> {
                viewModel.dispatchEvent(ChatViewModel.Event.ReplyRequested(action.bubble))
            }

            ChatAction.CancelReply -> {
                viewModel.dispatchEvent(ChatViewModel.Event.CancelReply)
            }

            is ChatAction.CashLinkOpened -> {
                viewModel.dispatchEvent(ChatViewModel.Event.CashLinkOpened(action.entropy))
            }

            is ChatAction.JumpToMessage -> {
                viewModel.dispatchEvent(ChatViewModel.Event.JumpToMessage(action.messageId))
            }

            ChatAction.JoinChat -> viewModel.dispatchEvent(ChatViewModel.Event.JoinChat)

            ChatAction.InviteToGroup -> {
                // The sheet, not the share sheet: copying the link is the other way to hand it out,
                // and going straight to the system share picker would bury it. The sheet reads the
                // url off the same state the CTA that got here is gated on.
                keyboard.hideIfVisible { navigator.push(ChatStep.InviteToGroup) }
            }

            is ChatAction.ViewProfile -> {
                // The triggers (top-bar tap, info-card chevron) are only clickable for subjects
                // that have a profile (see State.canViewProfile), so no gating is needed here.
                // Which profile depends on the subject: a DM's is its counterparty's, and a group
                // is its own — it has no participant to open one on.
                keyboard.hideIfVisible {
                    when (state.subject) {
                        is ChatSubject.Group -> navigator.push(ChatStep.GroupProfile)
                        else -> state.participant?.let { navigator.push(ChatStep.Profile(it)) }
                    }
                }
            }

            is ChatAction.ViewMemberProfile -> {
                // Resolved in the view model: the profile that drew the picture in the gutter is
                // the one to open, and the transcript already holds it.
                viewModel.memberParticipant(action.userId)?.let {
                    keyboard.hideIfVisible { navigator.push(ChatStep.Profile(it)) }
                }
            }
        }

        Unit
    }

    // Back unwinds the message actions before it leaves the conversation, innermost first: an edit
    // in progress, then the selection bar. Registered here rather than around the whole flow so
    // they are the innermost handlers and take the gesture before it pops the conversation.
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
        topBar = {
            ChatTopBar(
                navigator = navigator,
                state = state,
                onBarHeightChange = { barHeight = it },
                chatActionHandler = chatActionHandler,
                dispatch = viewModel::dispatchEvent,
            )
        },
        bottomBar = {
            UserControlBottomBar(
                state = state,
                hazeState = hazeState,
                onAction = chatActionHandler,
                dispatch = viewModel::dispatchEvent,
            )
        },
    ) { overlapPadding ->
        // The transcript and the info card go behind a blur together, because they are one surface
        // to the reader: legible metadata over an unreadable conversation would be the gate half
        // open. The title bar stays sharp — it is how a non-member knows what they are looking at.
        BlurredContent(
            modifier = Modifier.fillMaxSize(),
            enabled = state.isGatedPreview,
        ) {
            MessageList(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chat_message_list")
                    .softTopEdge(ChatTopEdge.blurHold(barHeight))
                    .hazeSource(hazeState),
                state = state,
                contentPadding = overlapPadding,
                messages = messages,
                separatorConfig = state.separatorConfig,
                otherReadPointer = otherReadPointer,
                onAction = chatActionHandler,
                linkCardResolution = viewModel.linkCardResolution,
                canViewProfile = state.canViewProfile,
                onJumpConsumed = { viewModel.dispatchEvent(ChatViewModel.Event.JumpConsumed) },
            )
        }
    }
}
