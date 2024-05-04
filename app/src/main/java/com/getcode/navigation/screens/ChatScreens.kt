package com.getcode.navigation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.error
import com.getcode.R
import com.getcode.analytics.AnalyticsManager
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.model.ID
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SheetTitleText
import com.getcode.ui.utils.getActivityScopedViewModel
import com.getcode.ui.components.chat.localized
import com.getcode.util.formatDateRelatively
import com.getcode.view.main.balance.BalanceScreeen
import com.getcode.view.main.balance.BalanceSheetViewModel
import com.getcode.view.main.chat.ChatScreen
import com.getcode.view.main.chat.ChatViewModel
import com.getcode.view.main.chat.conversation.ChatConversationScreen
import com.getcode.view.main.chat.conversation.ConversationViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data object BalanceModal : ChatGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    override val name: String
        @Composable get() = stringResource(id = R.string.title_balance)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        val viewModel = getActivityScopedViewModel<BalanceSheetViewModel>()
        val state by viewModel.stateFlow.collectAsState()
        val isViewingBuckets by remember(state.isBucketDebuggerVisible) {
            derivedStateOf { state.isBucketDebuggerVisible }
        }

        ModalContainer(
            navigator = navigator,
            onLogoClicked = {},
            titleString = { null },
            backButton = { isViewingBuckets },
            onBackClicked = isViewingBuckets.takeIf { it }?.let {
                {
                    viewModel.dispatchEvent(
                        BalanceSheetViewModel.Event.OnDebugBucketsVisible(false)
                    )
                }
            },
            closeButton = close@{
                if (viewModel.stateFlow.value.isBucketDebuggerVisible) return@close false
                if (navigator.isVisible) {
                    it is BalanceModal
                } else {
                    navigator.progress > 0f
                }
            },
            onCloseClicked = null,
        ) {
            BalanceScreeen(state = state, dispatch = viewModel::dispatchEvent)
        }

        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.Balance
        )

        LifecycleEffect(
            onDisposed = {
                viewModel.dispatchEvent(
                    BalanceSheetViewModel.Event.OnDebugBucketsVisible(
                        false
                    )
                )
            }
        )
    }
}

@Parcelize
data class ChatScreen(val chatId: ID) : ChatGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val vm = getViewModel<ChatViewModel>()
        val state by vm.stateFlow.collectAsState()
        val navigator = LocalCodeNavigator.current

        ModalContainer(
            titleString = { state.title.localized },
            backButton = { it is ChatScreen },
        ) {
            val messages = vm.chatMessages.collectAsLazyPagingItems()
            ChatScreen(state = state, messages = messages, dispatch = vm::dispatchEvent)
        }

        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<ChatViewModel.Event.OpenMessageChat>()
                .map { it.messageId }
                .onEach { navigator.push(ChatMessageConversationScreen(it)) }
                .launchIn(this)
        }

        LaunchedEffect(chatId) {
            vm.dispatchEvent(ChatViewModel.Event.OnChatIdChanged(chatId))
        }
    }
}

@Parcelize
data class ChatMessageConversationScreen(val messageId: ID) : ChatGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val vm = getViewModel<ConversationViewModel>()
        val state by vm.stateFlow.collectAsState()

        ModalContainer(
            title = {
                if (state.user == null) {
                    SheetTitleText(text = state.title)
                    return@ModalContainer
                }
                val user = state.user!!
                Row(modifier = Modifier
                    .padding(start = CodeTheme.dimens.staticGrid.x6)
                    .align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
                ) {
                    AsyncImage(
                        modifier = Modifier
                            .padding(start = CodeTheme.dimens.grid.x7)
                            .size(30.dp)
                            .clip(CircleShape),
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(user.imageUrl)
                            .error(R.drawable.ic_placeholder_user)
                            .build(),
                        contentDescription = null,
                    )
                    Column {
                        Text(text = user.username,
                            style = CodeTheme.typography.subtitle2
                        )
                        state.lastSeen?.let {
                            Text(
                                text = "Last seen ${it.formatDateRelatively()}",
                                style = CodeTheme.typography.caption,
                                color = BrandLight,
                            )
                        }
                    }
                }
            },
            backButton = { it is ChatMessageConversationScreen },
        ) {
            val messages = vm.messages.collectAsLazyPagingItems()
            ChatConversationScreen(state, messages, vm::dispatchEvent)
        }


        LaunchedEffect(messageId) {
            vm.dispatchEvent(ConversationViewModel.Event.OnMessageIdChanged(messageId))
        }
    }
}