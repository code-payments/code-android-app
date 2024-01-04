package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.manager.AnalyticsManager
import com.getcode.model.Currency
import com.getcode.models.Bill
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.util.RepeatOnLifecycle
import com.getcode.util.getActivityScopedViewModel
import com.getcode.view.main.account.AccountHome
import com.getcode.view.main.account.AccountSheetViewModel
import com.getcode.view.main.account.withdraw.AccountWithdrawAmountViewModel
import com.getcode.view.main.balance.BalanceSheet
import com.getcode.view.main.balance.BalanceSheetViewModel
import com.getcode.view.main.currency.CurrencySelectionSheet
import com.getcode.view.main.currency.CurrencyViewModel
import com.getcode.view.main.getKin.GetKinSheet
import com.getcode.view.main.giveKin.GiveKinSheet
import com.getcode.view.main.giveKin.GiveKinSheetViewModel
import com.getcode.view.main.home.HomeScreen
import com.getcode.view.main.home.HomeViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@Parcelize
data class HomeScreen(val cashLink: String? = null) : AppScreen(), MainGraph {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val vm = getActivityScopedViewModel<HomeViewModel>()
        HomeScreen(vm, cashLink)

        OnScreenResult<Bill> {
            Timber.d("onShowBill=${it.amount.fiat}")
            vm.showBill(it, vibrate = true)
        }
    }
}

@Parcelize
data object GetKinModal : MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        ModalContainer(
            closeButton = {
                if (navigator.isVisible) {
                    it is GetKinModal
                } else {
                    navigator.progress > 0f
                }
            },
        ) {
            GetKinSheet(getViewModel(), getActivityScopedViewModel())
        }
    }
}

@Parcelize
data object GiveKinModal : AppScreen(), MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        ModalContainer(
            closeButton = {
                if (navigator.isVisible) {
                    it is GiveKinModal
                } else {
                    navigator.progress > 0f
                }
            },
        ) {
            GiveKinSheet(getActivityScopedViewModel(), getViewModel())
        }


        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.GiveKin
        )
    }
}

@Parcelize
data object BalanceModal : MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        val viewModel = getViewModel<BalanceSheetViewModel>()
        val state by viewModel.stateFlow.collectAsState()
        val isViewingBuckets by remember(state.isDebugBucketsVisible) {
            derivedStateOf { state.isDebugBucketsVisible }
        }

        ModalContainer(
            navigator = navigator,
            onLogoClicked = {},
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
            BalanceSheet(state = state, dispatch = viewModel::dispatchEvent)
        }

        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.Balance
        )
    }
}

@Parcelize
data object AccountModal : MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getViewModel<AccountSheetViewModel>()
        ModalContainer(
            displayLogo = true,
            onLogoClicked = { viewModel.dispatchEvent(AccountSheetViewModel.Event.LogoClicked) },
            closeButton = {
                if (navigator.isVisible) {
                    it is AccountModal
                } else {
                    navigator.progress > 0f
                }
            }
        ) {
            AccountHome(viewModel)
        }

        LaunchedEffect(viewModel) {
            viewModel.dispatchEvent(AccountSheetViewModel.Event.Load)
        }

        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.Settings
        )
    }
}

@Parcelize
data object CurrencySelectionModal: MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        ModalContainer(
            closeButton = {
                if (navigator.isVisible) {
                    it is CurrencySelectionModal
                } else {
                    navigator.progress > 0f
                }
            }
        ) {
            CurrencySelectionSheet(viewModel = getActivityScopedViewModel())
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