package com.flipcash.app.balance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.balance.internal.WalletViewModel
import com.flipcash.app.balance.internal.WalletScreen
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun WalletScreen() {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val viewModel = hiltViewModel<WalletViewModel>()
        val tokenViewModel = hiltViewModel<SelectTokenViewModel>()
        WalletScreen(viewModel, tokenViewModel)

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

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<WalletViewModel.Event.SwitchTab>()
                .map { it.tab }
                // Same call the nav bar makes, so the tab arrives with the stack replaced.
                .onEach { navigator.replaceAll(it) }
                .launchIn(this)
        }
    }
}