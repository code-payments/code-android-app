package com.getcode.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.theme.sheetHeight
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.account.AccountFaq
import com.getcode.view.main.account.AccountFaqViewModel
import com.getcode.view.main.account.AccountHome
import com.getcode.view.main.account.AccountSheetViewModel
import com.getcode.view.main.home.HomeScan
import com.getcode.view.main.home.HomeViewModel

sealed interface MainGraph : Screen {
    fun readResolve(): Any = this
}

data class HomeScreen(val cashLink: String = ""): MainGraph {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val homeViewModel = getViewModel<HomeViewModel>()
        HomeScan(homeViewModel, cashLink)
    }
}

data object AccountModal: MainGraph, ModalRoot {
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getViewModel<AccountSheetViewModel>()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            SheetTitle(
                closeButton = navigator.lastItem is AccountModal,
                onCloseIconClicked = { navigator.hide() })
            AccountHome(viewModel = viewModel)
        }
    }

    data object Faq: MainGraph, NamedScreen {

        override val name: String
            @Composable get() = stringResource(id = R.string.title_faq)
        @Composable
        override fun Content() {
            val viewModel = getViewModel<AccountFaqViewModel>()
            AccountFaq(viewModel)
        }
    }
}