package com.flipcash.app.tipping

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.tipping.internal.TipFlowViewModel
import com.flipcash.app.tipping.internal.components.TipChatRow
import com.flipcash.features.tipping.R
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeScaffold

/**
 * The tip DM conversation list — always a step in the tipping [TippingFlowScreen] flow, so it shares
 * the flow's [TipFlowViewModel]. It is the "Chats" root tab: the standard centred screen title over
 * the list, no dismiss affordance (the root nav bar is the chrome); the tip card lives on its own
 * tab.
 */
@Composable
fun TipsScreen() {
    val viewModel = flowSharedViewModel<TipFlowViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_chats),
                // Centred rather than flush-start: an empty leading slot reserves no width, so a
                // Start title sits at the inset and reads as off-centre against the Add button.
                titleAlignment = Alignment.CenterHorizontally,
                // Node 9442:5779 — the only way to start a chat with someone who has never paid
                // you. A pushed route, not a step of this flow: this list is a tab home, and a step
                // pushed inside it would leave the tab bar over the entry screen.
                endContent = {
                    AppBarDefaults.Add { navigator.push(AppRoute.Messaging.NewChat) }
                },
            )
        }
    ) { padding ->
        val chats = state.tipChats
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Scroll anchor for UI tests: `send_contact_row` addresses a single row, this
                // addresses the scrollable list itself.
                .testTag("chat_list")
                .padding(padding)
                // After the padding so the fade lands on the list viewport — flush under the app
                // bar — rather than on the bar's own space. Rows dissolve into the background at
                // whichever edge is still scrollable instead of being cut off.
                .verticalScrollStateGradient(scrollState = listState),
            state = listState,
            // Clears the hoisted tab bar: keeps the last row reachable and centers the empty state
            // in the space the bar leaves visible.
            contentPadding = LocalTabBarPadding.current,
        ) {
            // Once the feed has loaded and there's nothing to show, the list is replaced by a
            // centered prompt.
            if (chats.isLoaded() && chats.data.isEmpty()) {
                item { NoChatsYet(Modifier.fillParentMaxSize()) }
            } else {
                tipChatItems(chats.dataOrNull.orEmpty()) { chat ->
                    navigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chat.chatId)))
                }
            }
        }
    }
}

/**
 * The "Chats" tab empty state (node 9340:2746) — bubble mark, title and prompt, centered in the
 * space the caller gives it (the list viewport).
 */
@Composable
private fun NoChatsYet(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                modifier = Modifier.size(CodeTheme.dimens.grid.x16),
                painter = painterResource(R.drawable.ic_bubble_outline),
                contentDescription = null,
                colorFilter = ColorFilter.tint(CodeTheme.colors.textMain),
            )

            Text(
                text = stringResource(R.string.title_noChatsYet),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
            )

            Text(
                modifier = Modifier.fillMaxWidth(0.6f),
                text = stringResource(R.string.description_noChatsYet),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun LazyListScope.tipChatItems(
    chats: List<ConversationReference>,
    onClick: (ConversationReference) -> Unit,
) {
    itemsIndexed(chats) { index, chat ->
        TipChatRow(
            chat = chat,
            showDivider = index < chats.lastIndex,
        ) {
            onClick(chat)
        }
    }
}

@Composable
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
private fun PreviewNoChatsYet() {
    NoChatsYet(Modifier.fillMaxSize())
}
