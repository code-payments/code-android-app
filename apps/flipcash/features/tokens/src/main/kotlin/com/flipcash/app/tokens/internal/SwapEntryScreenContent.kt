package com.flipcash.app.tokens.internal

import androidx.compose.runtime.Composable
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.shared.amountentry.AmountEntryScreen

@Composable
internal fun SwapEntryScreenContent(
    viewModel: SwapViewModel,
    largeHeader: Boolean = false,
    accessory: (@Composable () -> Unit)? = null,
) {
    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(SwapViewModel.Event.OnAmountConfirmed) },
        largeHeader = largeHeader,
        accessory = accessory,
    )
}
