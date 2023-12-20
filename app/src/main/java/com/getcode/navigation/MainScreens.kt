package com.getcode.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.theme.sheetHeight
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.account.AccountDeposit
import com.getcode.view.main.account.AccountFaq
import com.getcode.view.main.account.AccountFaqViewModel
import com.getcode.view.main.account.AccountHome
import com.getcode.view.main.account.AccountSheetViewModel
import com.getcode.view.main.getKin.BuyAndSellKin
import com.getcode.view.main.getKin.BuyAndSellKinViewModel
import com.getcode.view.main.home.HomeScan
import com.getcode.view.main.home.HomeViewModel

sealed interface MainGraph : Screen {
    fun readResolve(): Any = this
}

data class HomeScreen(val cashLink: String = "") : MainGraph {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        HomeScan(getViewModel(), cashLink)
    }
}

data object AccountModal : MainGraph, ModalRoot {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            AccountHome(getViewModel())
        }
    }
}

data object BuySellScreen : MainGraph {
    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            val navigator = LocalCodeNavigator.current

            SheetTitle(
                modifier = Modifier.padding(horizontal = 20.dp),
                // hide while transitioning to/from other destinations
                backButton = navigator.lastItem is BuySellScreen,
                closeButton = false,
                onBackIconClicked = { navigator.pop() }
            )
            BuyAndSellKin(getViewModel())
        }
    }
}

data object DepositKinScreen : MainGraph, NamedScreen {

    override val name: String
        @Composable get() = stringResource(id = R.string.title_depositKin)

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            val navigator = LocalCodeNavigator.current

            SheetTitle(
                modifier = Modifier.padding(horizontal = 20.dp),
                // hide while transitioning to/from other destinations
                backButton = navigator.lastItem is DepositKinScreen,
                closeButton = false,
                onBackIconClicked = { navigator.pop() }
            )
            AccountDeposit()
        }
    }
}

data object FaqScreen : MainGraph, NamedScreen {

    override val name: String
        @Composable get() = stringResource(id = R.string.title_faq)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            SheetTitle(
                modifier = Modifier.padding(horizontal = 20.dp),
                title = name,
                // hide while transitioning to/from other destinations
                backButton = navigator.lastItem is FaqScreen,
                closeButton = false,
                onBackIconClicked = { navigator.pop() })
            AccountFaq(getViewModel())
        }
    }
}