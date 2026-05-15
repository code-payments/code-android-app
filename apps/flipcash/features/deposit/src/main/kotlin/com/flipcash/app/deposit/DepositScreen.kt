package com.flipcash.app.deposit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.deposit.internal.DepositScreen
import com.flipcash.app.deposit.internal.DepositViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun DepositScreen(mint: Mint) {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<DepositViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_depositToken, state.tokenName.orEmpty()),
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { navigator.pop() },
        )
        DepositScreen(viewModel)
    }

    LaunchedEffect(viewModel, mint) {
        viewModel.dispatchEvent(DepositViewModel.Event.OnMintSelected(mint))
    }
}
