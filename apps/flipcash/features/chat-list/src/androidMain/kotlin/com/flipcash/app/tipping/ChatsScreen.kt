// kmp-audit: hiltViewModel and BackHandler are Android-only; the screen content is ChatsScreenContent in commonMain.
package com.flipcash.app.tipping

import androidx.activity.compose.BackHandler
import com.flipcash.app.tipping.internal.components.ChatListMoreButton
import com.flipcash.app.tipping.internal.components.ChatSelectionTopBar
import com.flipcash.app.tipping.internal.components.SelectionArchiveAction
import com.flipcash.services.models.chat.ChatId
import org.jetbrains.compose.resources.getPluralString
import androidx.compose.material.SnackbarResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.tipping.internal.ChatsViewModel
import com.flipcash.app.tipping.internal.HiltChatsViewModel
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.action_undo
import com.flipcash.core.resources.message_chatsArchived
import com.flipcash.core.resources.title_tabChat
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.toast.LocalFloatingToastHost
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The "Chats" root tab: tip DMs and groups under the standard centred screen title, with no dismiss
 * affordance (the root nav bar is the chrome). The tip card lives on the "You" tab.
 *
 * Wires [ChatsScreenContent] to the Android view model, the navigator and the root toast host.
 */
@Composable
fun ChatsScreen() {
    val viewModel = hiltViewModel<HiltChatsViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    // The root toast host, so the undo rises out of the tab bar on its glass.
    val toasts = LocalFloatingToastHost.current
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(Res.string.action_undo)

    // Archive with one Undo for everything it took: the swipe passes one chat, select mode several.
    val archiveWithUndo: (List<ChatId>) -> Unit = { ids ->
        if (toasts != null && ids.isNotEmpty()) {
            scope.launch {
                val result = toasts.show(
                    message = getPluralString(Res.plurals.message_chatsArchived, ids.size, ids.size),
                    icon = Icons.Outlined.Archive,
                    actionLabel = undoLabel,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.unarchive(ids)
            }
        }
    }

    // The view model picks the route for each way off the list; the screen only pushes it.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatsViewModel.Event.OpenScreen>()
            .onEach { navigator.push(it.screen) }
            .launchIn(this)
    }

    // Close or system back leaves select mode; deselecting the last row does not.
    BackHandler(enabled = state.isSelecting) { viewModel.endSelection() }

    ChatsScreenContent(
        state = state,
        onSelectFilter = viewModel::selectFilter,
        onRevealChips = viewModel::revealChips,
        onOpenArchived = viewModel::openArchived,
        onOpenChat = { chat -> viewModel.openChat(chat.chatId) },
        onMute = { chat -> navigator.push(AppRoute.Messaging.MuteChat(chat.chatId, chat.chatType)) },
        onArchive = { chat ->
            viewModel.archive(chat.chatId)
            // The row leaves the list at once, so offer to put it back.
            archiveWithUndo(listOf(chat.chatId))
        },
        onToggleRead = { chat -> viewModel.toggleRead(chat.chatId) },
        onStartSelection = viewModel::startSelection,
        onToggleSelection = viewModel::toggleSelection,
        topBar = {
            // Select mode takes the bar over, as a message selection does in a chat.
            ChatSelectionTopBar(
                selectionActions = if (state.isSelecting) state.selectionActions else null,
                archiveAction = SelectionArchiveAction.Archive,
                onClose = viewModel::endSelection,
                onRead = viewModel::readSelection,
                onMute = {
                    viewModel.muteSelection()?.let { (ids, types) ->
                        navigator.push(AppRoute.Messaging.MuteChats(ids, types))
                    }
                },
                onUnmute = viewModel::unmuteSelection,
                onArchive = { archiveWithUndo(viewModel.archiveSelection()) },
                label = "chat list bar",
            ) {
                AppBarWithTitle(
                    title = stringResource(Res.string.title_tabChat),
                    // Centred rather than flush-start: an empty leading slot reserves no width, so a
                    // Start title sits at the inset and reads as off-centre against the Add button.
                    titleAlignment = Alignment.CenterHorizontally,
                    // Node 13201:108 — opposite Add and drawn the same way.
                    leadingContent = {
                        ChatListMoreButton(
                            canReadAll = state.canReadAll,
                            onSelectChats = { viewModel.startSelection() },
                            onReadAll = viewModel::requestReadAll,
                        )
                    },
                    // Node 9442:5779 — the only way to start a chat with someone who has never paid
                    // you. A pushed route rather than an in-place screen: this list is a tab home, and
                    // anything drawn inside it would leave the tab bar over the entry screen.
                    endContent = {
                        AppBarDefaults.Add(onClick = viewModel::openNewChat)
                    },
                )
            }
        },
    )
}
