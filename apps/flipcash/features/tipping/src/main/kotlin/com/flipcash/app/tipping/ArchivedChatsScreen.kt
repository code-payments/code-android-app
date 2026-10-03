package com.flipcash.app.tipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.tipping.internal.ArchivedChatsViewModel
import com.flipcash.app.tipping.internal.components.TipChatRow
import com.flipcash.features.tipping.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SwipeAction
import com.getcode.ui.components.SwipeActionRow
import com.getcode.ui.components.SwipeRevealGroup
import com.getcode.ui.components.rememberSwipeRevealGroup
import com.getcode.ui.theme.CodeScaffold

/**
 * The archived chats: the same row as the Chats list, sorted by last activity. A swipe on a row
 * unarchives it. Opening a chat from here does not unarchive it.
 */
@Composable
fun ArchivedChatsScreen() {
    val viewModel = hiltViewModel<ArchivedChatsViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    ArchivedChatsContent(
        chats = state.chats.dataOrNull.orEmpty(),
        isLoaded = state.chats.isLoaded(),
        onBack = { navigator.pop() },
        onOpen = { chatId -> navigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chatId))) },
        onUnarchive = viewModel::unarchive,
    )
}

@Composable
private fun ArchivedChatsContent(
    chats: List<ConversationReference>,
    isLoaded: Boolean,
    onBack: () -> Unit,
    onOpen: (ChatId) -> Unit,
    onUnarchive: (ChatId) -> Unit,
) {
    // One row open at a time, as on the Chats list.
    val revealGroup = rememberSwipeRevealGroup()

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_archived),
                onBackIconClicked = onBack,
            )
        },
    ) { padding ->
        // One list for the rows and the empty state alike, so unarchiving the last chat fades its
        // row out and the message in, rather than swapping the whole list for the message at once.
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("archived_list"),
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
        ) {
            if (isLoaded && chats.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .animateItem()
                            .fillParentMaxSize()
                            .padding(bottom = padding.calculateBottomPadding()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                            text = stringResource(R.string.title_noArchivedChats),
                            style = CodeTheme.typography.textMedium,
                            color = CodeTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            // Keyed by chat so a row's swipe state stays with its chat as activity reorders.
            // animateItem fades an unarchived row out where it stands and slides the rest up.
            itemsIndexed(chats, key = { _, chat -> chat.chatId }) { index, chat ->
                UnarchiveSwipeRow(
                    modifier = Modifier.animateItem(),
                    onUnarchive = { onUnarchive(chat.chatId) },
                    stateKey = chat.chatId,
                    revealGroup = revealGroup,
                ) {
                    // The row draws its divider below itself, so none sits above the first row.
                    TipChatRow(chat = chat, showDivider = index < chats.lastIndex) {
                        onOpen(chat.chatId)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnarchiveSwipeRow(
    onUnarchive: () -> Unit,
    stateKey: Any,
    revealGroup: SwipeRevealGroup,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val label = stringResource(R.string.content_description_unarchiveChat)
    SwipeActionRow(
        actions = listOf(
            SwipeAction(
                background = CodeTheme.colors.surfaceVariant,
                onTriggered = onUnarchive,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Unarchive,
                    contentDescription = label,
                    tint = CodeTheme.colors.textMain,
                    modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
                )
            },
        ),
        // A swipe is out of reach with a screen reader, so the row offers the action there.
        modifier = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(label) { onUnarchive(); true })
        },
        stateKey = stateKey,
        revealGroup = revealGroup,
        content = content,
    )
}
