package com.getcode.navigation.screens

import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.model.KinAmount
import com.getcode.models.DeepLinkRequest
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.SheetTitleDefaults
import com.getcode.ui.utils.RepeatOnLifecycle
import com.getcode.ui.utils.getActivityScopedViewModel
import com.getcode.utils.trace
import com.getcode.view.download.ShareDownloadScreen
import com.getcode.view.main.account.AccountHome
import com.getcode.view.main.account.AccountSheetViewModel
import com.getcode.view.main.balance.BalanceScreen
import com.getcode.view.main.balance.BalanceSheetViewModel
import com.getcode.view.main.giveKin.GiveKinScreen
import com.getcode.view.main.home.HomeScreen
import com.getcode.view.main.home.HomeViewModel
import com.getcode.view.main.requestKin.RequestKinScreen
import com.google.firebase.encoders.annotations.Encodable.Ignore
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

sealed interface HomeResult {
    data class Bill(val bill: com.getcode.models.Bill) : HomeResult
    data class Request(val amount: KinAmount) : HomeResult
    data class ConfirmTip(val amount: KinAmount) : HomeResult
    data object ShowTipCard : HomeResult
    data object CancelTipEntry: HomeResult
}

@Parcelize
data class HomeScreen(
    val seed: String? = null,
    val cashLink: String? = null,
    @IgnoredOnParcel
    val request: DeepLinkRequest? = null,
) : AppScreen(), MainGraph {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val vm = getViewModel<HomeViewModel>()

        trace("home rendered")
        HomeScreen(vm, cashLink, request)

        OnScreenResult<HomeResult> { result ->
            when (result) {
                is HomeResult.Bill -> {
                    vm.showBill(result.bill)
                }

                is HomeResult.Request -> {
                    vm.presentRequest(amount = result.amount, payload = null, request = null)
                }

                is HomeResult.ConfirmTip -> {
                    vm.presentTipConfirmation(result.amount)
                }

                is HomeResult.ShowTipCard -> {
                    vm.presentShareableTipCard()
                }

                is HomeResult.CancelTipEntry -> {
                    vm.cancelTipEntry()
                }
            }
        }
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
