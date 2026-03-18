package com.flipcash.app.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.transactions.internal.TransactionHistoryScreen
import com.flipcash.app.transactions.internal.TransactionHistoryViewModel
import com.flipcash.features.transactions.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun TransactionHistoryScreen(mint: Mint) {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            isInModal = true,
            title = stringResource(R.string.title_transactionHistory),
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { navigator.pop() }
        )
        val viewModel = hiltViewModel<TransactionHistoryViewModel>()
        TransactionHistoryScreen(viewModel)

        LaunchedEffect(viewModel, mint) {
            viewModel.dispatchEvent(TransactionHistoryViewModel.Event.OnMintProvided(mint))
        }
    }
}
