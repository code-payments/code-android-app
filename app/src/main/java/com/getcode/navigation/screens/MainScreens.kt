package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.models.Bill
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.util.RepeatOnLifecycle
import com.getcode.view.main.account.AccountHome
import com.getcode.view.main.account.AccountSheetViewModel
import com.getcode.view.main.balance.BalanceSheet
import com.getcode.view.main.getKin.GetKin
import com.getcode.view.main.giveKin.GiveKinSheet
import com.getcode.view.main.home.HomeScan
import com.getcode.view.main.home.HomeViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

sealed interface MainGraph : Screen {
    /**
     * Common HomeViewModel for main graph to share
     */
    val homeViewModel: HomeViewModel
        @Composable get() = getViewModel<HomeViewModel>()

    fun readResolve(): Any = this
}

data class HomeScreen(val cashLink: String? = null) : AppScreen(), MainGraph {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val vm = homeViewModel
        HomeScan(vm, cashLink)

        OnScreenResult<Bill> {
            Timber.d("onshowBill$it")
            vm.showBill(it, vibrate = true)
        }
    }
}

data object GetKinModal : MainGraph, ModalRoot {
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
            GetKin(getViewModel(), homeViewModel)
        }
    }
}

data object GiveKinModal : MainGraph, ModalRoot {
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
            GiveKinSheet(getViewModel(), getViewModel())
        }
    }
}

data object BalanceModal : MainGraph, ModalRoot {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        ModalContainer(
            closeButton = {
                if (navigator.isVisible) {
                    it is BalanceModal
                } else {
                    navigator.progress > 0f
                }
            },
        ) {
            BalanceSheet(getViewModel())
        }
    }
}

data object AccountModal : MainGraph, ModalRoot {
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
    }
}

@Composable
fun <T> AppScreen.OnScreenResult(block: (T) -> Unit) {
    RepeatOnLifecycle(targetState = Lifecycle.State.RESUMED) {
        result
            .filterNotNull()
            .mapNotNull { it as? T }
            .onEach { block(it) }
            .onEach { result.value = null }
            .launchIn(this)
    }
}