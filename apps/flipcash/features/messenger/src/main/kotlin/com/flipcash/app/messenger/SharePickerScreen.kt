package com.flipcash.app.messenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.messenger.internal.SharePickerViewModel
import com.flipcash.features.messenger.R
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.rememberKeyboardController

/**
 * Where a share from another app picks its conversation, behind `AppRoute.Messaging.SharePicker`.
 * Choosing replaces this screen with that chat, which stages what was shared in its composer.
 * Nothing is sent from here.
 */
@Composable
fun SharePickerScreen() {
    val viewModel = hiltViewModel<SharePickerViewModel>()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val keyboard = rememberKeyboardController()

    Column(modifier = Modifier.fillMaxSize().background(CodeTheme.colors.background).statusBarsPadding()) {
        AppBarWithTitle(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.title_shareToChat),
            titleAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(16.dp),
            endContent = { AppBarDefaults.Close(onClick = { navigator.pop() }) },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            val loaded = chats
            if (loaded == null) {
                // No rows until the list is known, so an early tap can't land on a row that
                // is about to be replaced.
                CodeCircularProgressIndicator(
                    strokeWidth = CodeTheme.dimens.thickBorder,
                    color = CodeTheme.colors.textSecondary,
                    modifier = Modifier.align(Alignment.Center).requiredSize(CodeTheme.dimens.staticGrid.x8),
                )
                return@Box
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(loaded, key = { it.chatId.toString() }) { chat ->
                    ShareTargetRow(chat) {
                        if (viewModel.choose(chat.chatId)) {
                            keyboard.hideIfVisible {
                                navigator.replace(
                                    AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chat.chatId))
                                )
                            }
                        } else {
                            navigator.pop()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareTargetRow(chat: ConversationReference, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ContactAvatar(
            image = chat.image,
            displayName = chat.name.orEmpty(),
            access = chat.avatarAccess,
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x4),
                text = chat.name.orEmpty(),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(color = CodeTheme.colors.divider)
        }
    }
}
