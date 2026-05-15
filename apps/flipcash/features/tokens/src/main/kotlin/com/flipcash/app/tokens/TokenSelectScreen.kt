package com.flipcash.app.tokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.AppRoute.Menu.Deposit
import com.flipcash.app.core.AppRoute.Transfers.Withdrawal
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.internal.SelectTokenScreen
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun TokenSelectScreen(purpose: TokenPurpose) {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            isInModal = true,
            title = stringResource(R.string.title_selectCurrency),
            backButton = true,
            onBackIconClicked = { navigator.pop() },
            titleAlignment = Alignment.CenterHorizontally,
        )
        val viewModel = hiltViewModel<SelectTokenViewModel>()
        SelectTokenScreen(viewModel)

        LaunchedEffect(viewModel) {
            viewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(purpose))
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<SelectTokenViewModel.Event.OpenScreen>()
                .map { it.route }
                .onEach { navigator.push(it) }
                .launchIn(this)
        }

        // handle the cases where we are inserted in a flow to select a token
        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
                .filter { it.fromUser }
                .map { it.mint }
                .onEach { token ->
                    when (purpose) {
                        TokenPurpose.Balance -> Unit
                        TokenPurpose.Select -> Unit
                        TokenPurpose.Withdraw -> {
                            navigator.push(Withdrawal(token))
                        }
                        TokenPurpose.Deposit -> {
                            navigator.push(Deposit(token))
                        }
                    }
                }.launchIn(this)
        }

        // handle the case where we are changing the selected token
        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<SelectTokenViewModel.Event.OnTokenChanged>()
                .onEach { navigator.pop() }
                .launchIn(this)
        }
    }
}
