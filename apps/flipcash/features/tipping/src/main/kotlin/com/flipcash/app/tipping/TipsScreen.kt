package com.flipcash.app.tipping

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.flipcash.app.core.tipping.TipStep
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
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
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

/**
 * The tip DM conversation list — always a step in the tipping [TippingFlowScreen] flow, so it shares
 * the flow's [TipFlowViewModel]. Only the chrome differs by [FeatureFlag.NewUi]:
 * - **v2**: the "Chats" root tab — a flush large title over the list, no dismiss affordance (the
 *   root nav bar is the chrome); the tip card lives on its own tab.
 * - **v1**: the "Tips" sheet step — centered title with a Close, and a sticky "Show Tip Card" button
 *   that advances the flow.
 */
@Composable
fun TipsScreen() {
    val features = LocalFeatureFlags.current
    // Collect rather than snapshot `.value` — the flow is seeded with the flag's default until
    // DataStore emits, so a remembered read freezes the default (see MenuScreenContent).
    val isNewUi by features.observe(FeatureFlag.NewUi).collectAsStateWithLifecycle()

    val viewModel = flowSharedViewModel<TipFlowViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    CodeScaffold(
        topBar = {
            if (isNewUi) {
                AppBarWithTitle(
                    title = stringResource(R.string.title_chats),
                    titleTextStyle = CodeTheme.typography.screenTitleLarge,
                )
            } else {
                AppBarWithTitle(
                    title = stringResource(R.string.title_tips),
                    titleAlignment = Alignment.CenterHorizontally,
                    endContent = {
                        AppBarDefaults.Close { navigator.hide() }
                    }
                )
            }
        }
    ) { padding ->
        val chats = state.tipChats
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Clears the hoisted v2 tab bar (empty for v1, which has no tab bar): keeps the last row
            // reachable and centers the empty state in the space the bar leaves visible.
            contentPadding = LocalTabBarPadding.current,
        ) {
            // v1 surfaces the tip card via a button here; v2 has a dedicated tip-card tab instead.
            if (!isNewUi) {
                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CodeTheme.dimens.inset)
                            .background(CodeTheme.colors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CodeButton(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = CodeTheme.dimens.inset),
                            text = stringResource(R.string.action_showTipCard),
                            buttonState = ButtonState.Filled,
                            onClick = {
                                navigator.navigate(TipStep.TipCard)
                            },
                        )
                    }
                }
            }

            // v2 only: once the feed has loaded and there's nothing to show, the list is replaced by
            // a centered prompt. v1's sheet keeps its bare list under the Tip Card button.
            if (isNewUi && chats.isLoaded() && chats.data.isEmpty()) {
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
