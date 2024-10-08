package com.getcode.navigation.screens

import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.currentOrThrow
import com.getcode.LocalSession
import com.getcode.R
import com.getcode.model.ID
import com.getcode.model.chat.Reference
import com.getcode.models.DeepLinkRequest
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.SheetTitleDefaults
import com.getcode.ui.components.chat.utils.localized
import com.getcode.ui.utils.RepeatOnLifecycle
import com.getcode.ui.utils.getActivityScopedViewModel
import com.getcode.utils.trace
import com.getcode.view.download.ShareDownloadScreen
import com.getcode.view.main.account.AccountHome
import com.getcode.view.main.account.AccountSheetViewModel
import com.getcode.view.main.balance.BalanceScreen
import com.getcode.view.main.balance.BalanceSheetViewModel
import com.getcode.view.main.chat.ChatScreen
import com.getcode.view.main.chat.NotificationCollectionViewModel
import com.getcode.view.main.giveKin.GiveKinScreen
import com.getcode.view.main.requestKin.RequestKinScreen
import com.getcode.view.main.scanner.ScanScreen
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScanScreen(
    val seed: String? = null,
    val cashLink: String? = null,
    @IgnoredOnParcel
    val request: DeepLinkRequest? = null,
) : AppScreen(), MainGraph {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        trace("home rendered")
        val session = LocalSession.currentOrThrow

        ScanScreen(session, cashLink, request)
    }
}

@Parcelize
data object GiveKinModal : AppScreen(), MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    override val name: String
        @Composable get() = stringResource(id = R.string.title_giveCash)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        ModalContainer(
            closeButtonEnabled = {
                if (navigator.isVisible) {
                    it is GiveKinModal
                } else {
                    navigator.progress > 0f
                }
            },
        ) {
            GiveKinScreen(getViewModel())
        }
    }
}

@Parcelize
data class RequestKinModal(
    val showClose: Boolean = false,
) : AppScreen(), MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    override val name: String
        @Composable get() = stringResource(id = R.string.title_requestKin)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        val content = @Composable {
            RequestKinScreen(getViewModel())
        }

        if (showClose) {
            ModalContainer(
                closeButtonEnabled = {
                    if (navigator.isVisible) {
                        it is RequestKinModal
                    } else {
                        navigator.progress > 0f
                    }
                }
            ) {
                content()
            }
        } else {
            ModalContainer(
                backButtonEnabled = {
                    if (navigator.isVisible) {
                        it is RequestKinModal
                    } else {
                        navigator.progress > 0f
                    }
                }
            ) {
                content()
            }
        }
    }
}

@Parcelize
data object AccountModal : MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getActivityScopedViewModel<AccountSheetViewModel>()
        ModalContainer(
            displayLogo = true,
            onLogoClicked = { viewModel.dispatchEvent(AccountSheetViewModel.Event.LogoClicked) },
            closeButtonEnabled = {
                if (navigator.isVisible) {
                    it is AccountModal
                } else {
                    navigator.progress > 0f
                }
            }
        ) {
            AccountHome(viewModel)
        }
    }
}

@Parcelize
data object ShareDownloadLinkModal : MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        ModalContainer(
            closeButtonEnabled = { it is ShareDownloadLinkModal }
        ) {
            ShareDownloadScreen()
        }
    }
}

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

        val backButton = @Composable {
            when {
                isViewingBuckets -> SheetTitleDefaults.BackButton()
                !isViewingBuckets && state.isBucketDebuggerEnabled -> {
                    Icon(
                        imageVector = Icons.Rounded.BubbleChart,
                        contentDescription = "",
                        tint = Color.White,
                    )
                }

                else -> Unit
            }
        }

        ModalContainer(
            navigator = navigator,
            onLogoClicked = {},
            backButton = backButton,
            backButtonEnabled = { isViewingBuckets || state.isBucketDebuggerEnabled },
            onBackClicked = when {
                isViewingBuckets -> {
                    {
                        viewModel.dispatchEvent(
                            BalanceSheetViewModel.Event.OnDebugBucketsVisible(false)
                        )
                    }
                }

                state.isBucketDebuggerEnabled -> {
                    {
                        viewModel.dispatchEvent(
                            BalanceSheetViewModel.Event.OnDebugBucketsVisible(true)
                        )
                    }
                }

                else -> null
            },
            closeButtonEnabled = close@{
                if (viewModel.stateFlow.value.isBucketDebuggerVisible) return@close false
                if (navigator.isVisible) {
                    it is BalanceModal
                } else {
                    navigator.progress > 0f
                }
            },
            onCloseClicked = null,
        ) {
            BalanceScreen(state = state, dispatch = viewModel::dispatchEvent)
        }

        LifecycleEffect(
            onStarted = {
                val disposedScreen = navigator.lastItem
                if (disposedScreen !is BalanceModal) {
                    viewModel.dispatchEvent(BalanceSheetViewModel.Event.OnOpened)
                }
            },
            onDisposed = {
                val disposedScreen = navigator.lastItem
                if (disposedScreen !is BalanceModal) {
                    viewModel.dispatchEvent(
                        BalanceSheetViewModel.Event.OnDebugBucketsVisible(false)
                    )
                }
            }
        )
    }
}

@Parcelize
data class NotificationCollectionScreen(val collectionId: ID) : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val vm = getViewModel<NotificationCollectionViewModel>()
        val state by vm.stateFlow.collectAsState()
        val navigator = LocalCodeNavigator.current

        ModalContainer(
            titleString = { state.title.localized },
            backButtonEnabled = { it is NotificationCollectionScreen },
        ) {
            val messages = vm.chatMessages.collectAsLazyPagingItems()
            ChatScreen(state = state, messages = messages, dispatch = vm::dispatchEvent)
        }

        LaunchedEffect(vm) {
            vm.eventFlow
                .filterIsInstance<NotificationCollectionViewModel.Event.OpenMessageChat>()
                .map { it.reference }
                .filterIsInstance<Reference.IntentId>()
                .map { it.id }
                .onEach { navigator.push(ConversationScreen(intentId = it)) }
                .launchIn(this)
        }

        LaunchedEffect(collectionId) {
            vm.dispatchEvent(NotificationCollectionViewModel.Event.OnChatIdChanged(collectionId))
        }
    }
}

@Composable
fun <T> AppScreen.OnScreenResult(block: (T) -> Unit) {
    RepeatOnLifecycle(
        targetState = Lifecycle.State.RESUMED,
    ) {
        result
            .filterNotNull()
            .mapNotNull { it as? T }
            .onEach { runCatching { block(it) } }
            .onEach { result.value = null }
            .launchIn(this)
    }
}
