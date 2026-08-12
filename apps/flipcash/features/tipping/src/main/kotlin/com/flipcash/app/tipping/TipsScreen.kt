package com.flipcash.app.tipping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.tipping.TipStep
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
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
    val isNewUi = remember(features) { features.observe(FeatureFlag.NewUi).value }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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

            tipChatItems(state.tipChats) { chat ->
                navigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chat.chatId)))
            }
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
