// kmp-audit: hiltViewModel is Android-only; the screen content is ChatsScreenContent in commonMain.
package com.flipcash.app.tipping

import androidx.compose.material.SnackbarResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.tipping.internal.HiltChatsViewModel
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.action_undo
import com.flipcash.core.resources.message_chatArchived
import com.flipcash.core.resources.title_tabChat
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.toast.LocalFloatingToastHost
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
    val archivedMessage = stringResource(Res.string.message_chatArchived)
    val undoLabel = stringResource(Res.string.action_undo)

    ChatsScreenContent(
        state = state,
        onSelectFilter = viewModel::selectFilter,
        onRevealChips = viewModel::revealChips,
        onOpenArchived = { navigator.push(AppRoute.Messaging.ArchivedChats) },
        onOpenChat = { chat ->
            navigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chat.chatId)))
        },
        onMute = { chat -> navigator.push(AppRoute.Messaging.MuteChat(chat.chatId, chat.chatType)) },
        onArchive = { chat ->
            viewModel.archive(chat.chatId)
            // The row leaves the list at once, so offer to put it back.
            if (toasts != null) {
                scope.launch {
                    val result = toasts.show(
                        message = archivedMessage,
                        icon = Icons.Outlined.Archive,
                        actionLabel = undoLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.unarchive(chat.chatId)
                }
            }
        },
        topBar = {
            AppBarWithTitle(
                title = stringResource(Res.string.title_tabChat),
                // Centred rather than flush-start: an empty leading slot reserves no width, so a
                // Start title sits at the inset and reads as off-centre against the Add button.
                titleAlignment = Alignment.CenterHorizontally,
                // Node 9442:5779 — the only way to start a chat with someone who has never paid
                // you. A pushed route rather than an in-place screen: this list is a tab home, and
                // anything drawn inside it would leave the tab bar over the entry screen.
                endContent = {
                    AppBarDefaults.Add { navigator.push(AppRoute.Messaging.NewChat) }
                },
            )
        },
    )
}
