package com.flipcash.app.cash.internal

import androidx.compose.runtime.Composable
import com.flipcash.app.core.AppRoute
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.navigation.core.LocalCodeNavigator

@Composable
internal fun GiveScreenContent(viewModel: CashScreenViewModel) {
    val navigator = LocalCodeNavigator.current

    AmountEntryScreen(
        delegate = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(CashScreenViewModel.Event.OnGive) },
        onChangeCurrency = { navigator.push(AppRoute.Main.RegionSelection) },
    )
}
