package com.getcode.navigation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.model.ID
import com.getcode.model.TwitterUser
import com.getcode.model.chat.Reference
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.util.formatDateRelatively
import com.getcode.view.main.chat.conversation.ChatConversationScreen
import com.getcode.view.main.chat.conversation.ConversationViewModel
import com.getcode.view.main.chat.create.byusername.ChatByUsernameScreen
import com.getcode.view.main.chat.list.ChatListScreen
import com.getcode.view.main.chat.list.ChatListViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data object ChatListModal: ChatGraph, ModalRoot {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_chat)

    @Composable
    override fun Content() {
        ModalContainer(
            closeButtonEnabled = { it is ChatListModal },
        ) {
            val viewModel = getViewModel<ChatListViewModel>()
            val conversations = viewModel.conversations.collectAsLazyPagingItems()
            ChatListScreen(viewModel, conversations)
        }
    }
}

@Parcelize
data object ChatByUsernameScreen: ChatGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_whatsTheirUsername)

    @Composable
    override fun Content() {
        ModalContainer(
            backButtonEnabled = { it is ChatByUsernameScreen },
        ) {
            ChatByUsernameScreen(getViewModel())
        }
    }
}

@Parcelize
data class ChatMessageConversationScreen(
    val user: @RawValue TwitterUser? = null,
    val chatId: ID? = null,
    val intentId: ID? = null
) : AppScreen(), ChatGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val vm = getViewModel<ConversationViewModel>()
        val state by vm.stateFlow.collectAsState()

        ModalContainer(
            title = {
                Row(
                    modifier = Modifier
                        .padding(start = CodeTheme.dimens.staticGrid.x6)
                        .align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((-8).dp)
                    ) {
                        val imageModifier = Modifier
                            .padding(start = CodeTheme.dimens.grid.x7)
                            .size(CodeTheme.dimens.staticGrid.x6)
                            .clip(CircleShape)

                        state.users.fastForEachIndexed { index, user ->
                            UserAvatar(
                                modifier = imageModifier
                                    .zIndex((state.users.size - index).toFloat()),
                                data = if (user.isRevealed) {
                                    user.imageUrl
                                } else {
                                    user.memberId
                                }
                            )
                        }

                        if (state.users.isEmpty()) {
                            Spacer(modifier = Modifier.requiredWidth(CodeTheme.dimens.grid.x3))
                        }
                    }

                    Column {
                        val title = state.users.mapNotNull { it.username }
                            .joinToString()
                            .takeIf { it.isNotEmpty() } ?: "Anonymous Tipper"
                        Text(
                            text = title,
                            style = CodeTheme.typography.screenTitle
                        )
                        state.lastSeen?.let {
                            Text(
                                text = "Last seen ${it.formatDateRelatively()}",
                                style = CodeTheme.typography.caption,
                                color = CodeTheme.colors.textSecondary,
                            )
                        }
                    }
                }

            },
            backButtonEnabled = { it is ChatMessageConversationScreen },
            onBackClicked = {
                if (state.twitterUser != null) {
                    navigator.popUntil { it is ChatListModal }
                } else {
                    navigator.pop()
                }
            }
        ) {
            val messages = vm.messages.collectAsLazyPagingItems()
            ChatConversationScreen(state, messages, vm::dispatchEvent)
        }

        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<ConversationViewModel.Event.SendCash>()
                .onEach {
                    navigator.push(EnterTipModal(isInChat = true))
                }.launchIn(this)
        }

        val context = LocalContext.current
        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<ConversationViewModel.Event.Error>()
                .onEach {
                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                    if (it.fatal) {
                        navigator.pop()
                    }
                }.launchIn(this)
        }

        LaunchedEffect(user) {
            if (user != null) {
                vm.dispatchEvent(
                    ConversationViewModel.Event.OnTwitterUserChanged(user)
                )
            }
        }

        LaunchedEffect(chatId) {
            if (chatId != null) {
                vm.dispatchEvent(
                    ConversationViewModel.Event.OnChatIdChanged(chatId)
                )
            }
        }

        LaunchedEffect(intentId) {
            if (intentId != null) {
                vm.dispatchEvent(
                    ConversationViewModel.Event.OnReferenceChanged(Reference.IntentId(intentId))
                )
            }
        }
    }
}