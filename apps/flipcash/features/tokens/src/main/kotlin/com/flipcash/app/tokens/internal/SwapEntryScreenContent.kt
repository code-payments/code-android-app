package com.flipcash.app.tokens.internal

import androidx.compose.runtime.Composable
import com.flipcash.app.core.AppRoute
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.navigation.core.LocalCodeNavigator

@Composable
internal fun SwapEntryScreenContent(
    viewModel: SwapViewModel,
) {
    val navigator = LocalCodeNavigator.current

    AmountEntryScreen(
        delegate = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(SwapViewModel.Event.OnAmountConfirmed) },
        onChangeCurrency = { navigator.push(AppRoute.Main.RegionSelection) },
    )
}
