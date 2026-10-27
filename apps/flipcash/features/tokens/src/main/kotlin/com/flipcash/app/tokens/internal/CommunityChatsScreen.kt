package com.flipcash.app.tokens.internal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.tokens.internal.components.info.CommunityChatRow
import com.flipcash.app.tokens.ui.CommunityChatsViewModel
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold

/** "Top Community Chats", View All's destination: every chat the server ranked for [mint]. */
@Composable
fun CommunityChatsScreen(mint: Mint) {
    val viewModel = hiltViewModel<CommunityChatsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    LaunchedEffect(viewModel, mint) { viewModel.load(mint) }

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_topCommunityChats),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { navigator.pop() },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x2,
            ),
        ) {
            items(state.chats, key = { it.chatId.toString() }) { chat ->
                CommunityChatRow(
                    chat = chat,
                    currencyName = state.currencyName,
                    onClick = {
                        navigator.push(
                            AppRoute.Messaging.Chat(
                                identifier = ChatIdentifier.ByChatId(chat.chatId),
                                openOnProfile = true,
                            )
                        )
                    },
                )
            }
        }
    }
}
