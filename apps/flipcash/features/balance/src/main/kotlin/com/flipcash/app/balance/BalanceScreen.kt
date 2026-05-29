package com.flipcash.app.balance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.balance.internal.BalanceScreen
import com.flipcash.app.balance.internal.BalanceViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun BalanceScreen() {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_wallet),
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            endContent = {
                AppBarDefaults.Close { navigator.hide() }
            }
        )

        val viewModel = hiltViewModel<BalanceViewModel>()
        val tokenViewModel = hiltViewModel<SelectTokenViewModel>()
        BalanceScreen(viewModel, tokenViewModel)

        LaunchedEffect(tokenViewModel) {
            tokenViewModel.dispatchEvent(
                SelectTokenViewModel.Event.OnPurposeChanged(
                    TokenPurpose.Balance
                )
            )
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<BalanceViewModel.Event.OpenCurrencySelection>()
                .onEach {
                    navigator.push(AppRoute.Main.RegionSelection)
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<BalanceViewModel.Event.OpenScreen>()
                .map { it.screen }
                .onEach { navigator.push(it) }
                .launchIn(this)
        }
    }
}