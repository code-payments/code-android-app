package com.flipcash.app.withdrawal.internal.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.solana.keys.Mint

@Composable
internal fun WithdrawalEntryScreen(
    viewModel: WithdrawalViewModel,
    mint: Mint,
    onOpenRegionSelection: () -> Unit,
) {
    val config by viewModel.config.collectAsStateWithLifecycle()

    AmountEntryScreen(
        delegate = viewModel.amountDelegate,
        config = config,
        onConfirm = { viewModel.dispatchEvent(WithdrawalViewModel.Event.OnAmountConfirmed) },
        onChangeCurrency = onOpenRegionSelection,
    )

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(WithdrawalViewModel.Event.OnMintSelected(mint))
    }
}
