package com.flipcash.app.cash.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.navigation.core.LocalCodeNavigator

@Composable
internal fun GiveScreenContent(viewModel: CashScreenViewModel) {
    val navigator = LocalCodeNavigator.current
    val config by viewModel.config.collectAsStateWithLifecycle()

    AmountEntryScreen(
        delegate = viewModel.amountDelegate,
        config = config,
        onConfirm = { viewModel.dispatchEvent(CashScreenViewModel.Event.OnGive) },
        onChangeCurrency = { navigator.push(AppRoute.Main.RegionSelection) },
    )
}
