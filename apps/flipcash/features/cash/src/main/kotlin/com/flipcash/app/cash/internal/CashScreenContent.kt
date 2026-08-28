package com.flipcash.app.cash.internal

import androidx.compose.runtime.Composable
import com.flipcash.shared.amountentry.AmountEntryScreen

@Composable
internal fun GiveScreenContent(viewModel: CashScreenViewModel) {
    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(CashScreenViewModel.Event.OnGive) },
        largeHeader = true,
    )
}
