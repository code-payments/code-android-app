package com.flipcash.desktop.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flipcash.app.tipping.ChatsScreenContent
import com.flipcash.app.tipping.internal.ChatsViewModel
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.title_tabChat
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.theme.CodeTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The sidebar's chat list: the shared [ChatsScreenContent] on desktop's [ChatsViewModel]. Archived
 * chats, mute and new chat have no desktop screen yet, so those actions do nothing here.
 */
@Composable
fun DesktopChatList(
    viewModel: ChatsViewModel,
    onOpenChat: (ConversationReference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.stateFlow.collectAsState()
    Box(modifier) {
        ChatsScreenContent(
            state = state,
            onSelectFilter = viewModel::selectFilter,
            onRevealChips = viewModel::revealChips,
            onOpenArchived = {},
            onOpenChat = onOpenChat,
            onMute = {},
            onArchive = { chat -> viewModel.archive(chat.chatId) },
            topBar = {
                // AppBarWithTitle is Android-only; the title row matches its 16dp inset.
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.title_tabChat), style = CodeTheme.typography.screenTitle)
                }
            },
        )
    }
}
