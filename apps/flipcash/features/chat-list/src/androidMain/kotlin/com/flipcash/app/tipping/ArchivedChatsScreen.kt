// kmp-audit: hiltViewModel and BackHandler are Android-only; the screen content is ArchivedChatsContent in commonMain.
package com.flipcash.app.tipping

import com.flipcash.app.tipping.internal.components.ChatSelectionTopBar
import com.flipcash.app.tipping.internal.components.SelectionArchiveAction
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.tipping.internal.HiltArchivedChatsViewModel
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.title_archived
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle
import org.jetbrains.compose.resources.stringResource

/**
 * The archived chats: the same row as the Chats list, sorted by last activity. A swipe on a row
 * unarchives it. Opening a chat from here does not unarchive it.
 */
@Composable
fun ArchivedChatsScreen() {
    val viewModel = hiltViewModel<HiltArchivedChatsViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    // Close or system back leaves select mode; deselecting the last row does not.
    BackHandler(enabled = state.isSelecting) { viewModel.endSelection() }

    ArchivedChatsContent(
        chats = state.chats.dataOrNull.orEmpty(),
        isLoaded = state.chats.isLoaded(),
        onOpen = { chatId -> navigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chatId))) },
        onUnarchive = viewModel::unarchive,
        selection = state.selection,
        onStartSelection = viewModel::startSelection,
        onToggleSelection = viewModel::toggleSelection,
        onToggleRead = viewModel::toggleRead,
        topBar = {
            ChatSelectionTopBar(
                selectionActions = if (state.isSelecting) state.selectionActions else null,
                archiveAction = SelectionArchiveAction.Unarchive,
                onClose = viewModel::endSelection,
                onRead = viewModel::readSelection,
                onMute = {
                    viewModel.muteSelection()?.let { (ids, types) ->
                        navigator.push(AppRoute.Messaging.MuteChats(ids, types))
                    }
                },
                onUnmute = viewModel::unmuteSelection,
                onArchive = viewModel::unarchiveSelection,
                label = "archived list bar",
            ) {
                AppBarWithTitle(
                    title = stringResource(Res.string.title_archived),
                    titleAlignment = Alignment.CenterHorizontally,
                    onBackIconClicked = { navigator.pop() },
                )
            }
        },
    )
}
