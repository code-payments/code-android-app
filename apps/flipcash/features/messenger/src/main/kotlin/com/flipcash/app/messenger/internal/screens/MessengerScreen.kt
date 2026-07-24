package com.flipcash.app.messenger.internal.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.core.AppRoute
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.messenger.internal.screens.components.MessageList
import com.flipcash.app.messenger.internal.screens.components.UserControlBottomBar
import com.flipcash.shared.chat.models.ChatAction
import com.getcode.navigation.core.LocalCodeNavigator
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

    ChatInputScaffold(
        topBar = { ChatTopBar(navigator, state.participant) },
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
            onAction = { action ->
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
                }
            },
        )
    }
}

@Composable
private fun ChatInputScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Overlay scaffold: the content fills the whole area (drawn behind the blurred bars) and is
    // inset by the bar heights. SubcomposeLayout measures the bars BEFORE the content in the same
    // layout pass, so the content receives correct padding on the very first frame. The previous
    // onSizeChanged approach fed 0 padding on frame 1 and snapped to the real heights on frame 2,
    // which made the message list visibly jump on every open and every pop-back.
    SubcomposeLayout(
        modifier = Modifier
            .imePadding()
            .testTag("chat_screen"),
    ) { constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val topPlaceables = subcompose(ChatScaffoldSlot.Top, topBar).map { it.measure(looseConstraints) }
        val bottomPlaceables = subcompose(ChatScaffoldSlot.Bottom, bottomBar).map { it.measure(looseConstraints) }
        val topHeight = topPlaceables.maxOfOrNull { it.height } ?: 0
        val bottomHeight = bottomPlaceables.maxOfOrNull { it.height } ?: 0

        val padding = PaddingValues(
            top = topHeight.toDp(),
            bottom = bottomHeight.toDp(),
        )

        val contentPlaceables = subcompose(ChatScaffoldSlot.Content) { content(padding) }
            .map { it.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            contentPlaceables.forEach { it.place(0, 0) }
            topPlaceables.forEach { it.place((constraints.maxWidth - it.width) / 2, 0) }
            bottomPlaceables.forEach {
                it.place((constraints.maxWidth - it.width) / 2, constraints.maxHeight - it.height)
            }
        }
    }
}

private enum class ChatScaffoldSlot { Top, Bottom, Content }
