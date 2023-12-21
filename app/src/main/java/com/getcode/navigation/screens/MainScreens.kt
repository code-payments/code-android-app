package com.getcode.navigation.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.view.login.PhoneConfirm
import com.getcode.view.main.account.AccountAccessKey
import com.getcode.view.main.account.AccountDebugOptions
import com.getcode.view.main.account.AccountDeposit
import com.getcode.view.main.account.AccountDetails
import com.getcode.view.main.account.AccountFaq
import com.getcode.view.main.account.AccountHome
import com.getcode.view.main.account.AccountSheetViewModel
import com.getcode.view.main.account.ConfirmDeleteAccount
import com.getcode.view.main.account.DeleteCodeAccount
import com.getcode.view.main.balance.BalanceSheet
import com.getcode.view.main.getKin.BuyAndSellKin
import com.getcode.view.main.getKin.GetKin
import com.getcode.view.main.getKin.GetKinSheet
import com.getcode.view.main.giveKin.GiveKinSheet
import com.getcode.view.main.home.HomeScan
import timber.log.Timber

sealed interface MainGraph : Screen {
    fun readResolve(): Any = this
}

data class HomeScreen(val cashLink: String? = null) : MainGraph {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        HomeScan(getViewModel(), cashLink)
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
            GetKin(getViewModel(), getViewModel())
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
            GiveKinSheet(getViewModel(), getViewModel(), getViewModel())
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