package com.flipcash.app.balance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.balance.internal.HiltWalletViewModel
import com.flipcash.app.balance.internal.MoneyTitleBar
import com.flipcash.app.balance.internal.WalletViewModel
import com.flipcash.app.balance.internal.WalletScreen
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.HiltSelectTokenViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * The wallet tab's home.
 *
 * @param money Lays the wallet out as the Money tab of the group money shell (node 13729:744).
 * @param profileAvatar The viewer's avatar in the Money title bar; tapping it opens You.
 * @param onProfileLongClick What a long press on that avatar does: the app passes the You tab's
 * long press, the account switcher, or null while the switcher's gate is closed.
 */
@Composable
fun WalletScreen(
    money: Boolean = false,
    profileAvatar: @Composable () -> Unit = {},
    onProfileLongClick: (() -> Unit)? = null,
) {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val viewModel = hiltViewModel<HiltWalletViewModel>()
        val tokenViewModel = hiltViewModel<HiltSelectTokenViewModel>()
        WalletScreen(
            viewModel,
            tokenViewModel,
            money = money,
            topBar = {
                MoneyTitleBar(
                    profileAvatar = profileAvatar,
                    dispatchEvent = viewModel::dispatchEvent,
                    onProfileLongClick = onProfileLongClick,
                )
            },
        )

        LaunchedEffect(tokenViewModel) {
            tokenViewModel.dispatchEvent(
                SelectTokenViewModel.Event.OnPurposeChanged(
                    TokenPurpose.Balance
                )
            )
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<WalletViewModel.Event.OpenCurrencySelection>()
                .onEach {
                    navigator.openAsSheet(AppRoute.Main.RegionSelection)
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<WalletViewModel.Event.OpenScreen>()
                .map { it.screen }
                .onEach { navigator.push(it) }
                .launchIn(this)
        }
    }
}