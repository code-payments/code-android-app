package com.flipcash.app.withdrawal.internal.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.solana.keys.Mint

@Composable
internal fun WithdrawalEntryScreen(
    viewModel: WithdrawalViewModel,
    mint: Mint,
) {
    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(WithdrawalViewModel.Event.OnAmountConfirmed) },
    )

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(WithdrawalViewModel.Event.OnMintSelected(mint))
    }
}
