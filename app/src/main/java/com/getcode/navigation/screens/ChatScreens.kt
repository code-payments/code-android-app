package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.analytics.AnalyticsManager
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.model.ID
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.utils.getActivityScopedViewModel
import com.getcode.ui.components.chat.localized
import com.getcode.view.main.balance.BalanceScreeen
import com.getcode.view.main.balance.BalanceSheetViewModel
import com.getcode.view.main.chat.ChatScreen
import com.getcode.view.main.chat.ChatViewModel
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
        val isViewingBuckets by remember(state.isDebugBucketsVisible) {
            derivedStateOf { state.isDebugBucketsVisible }
        }

        ModalContainer(
            navigator = navigator,
            onLogoClicked = {},
            title = { null },
            backButton = { isViewingBuckets },
            onBackClicked = isViewingBuckets.takeIf { it }?.let {
                {
                    viewModel.dispatchEvent(
                        BalanceSheetViewModel.Event.OnDebugBucketsVisible(false)
                    )
                }
            },
            closeButton = close@{
                if (viewModel.stateFlow.value.isDebugBucketsVisible) return@close false
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
        ModalContainer(
            title = { state.title.localized },
            backButton = { it is ChatScreen },
        ) {
            val messages = vm.chatMessages.collectAsLazyPagingItems()
            ChatScreen(state = state, messages = messages, dispatch = vm::dispatchEvent)
        }

        LaunchedEffect(chatId) {
            vm.dispatchEvent(ChatViewModel.Event.OnChatIdChanged(chatId))
        }
    }
}