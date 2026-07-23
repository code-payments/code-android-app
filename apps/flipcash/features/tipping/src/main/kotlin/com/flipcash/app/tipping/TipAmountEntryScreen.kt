package com.flipcash.app.tipping

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.tipping.internal.TipAmountEntryViewModel
import com.flipcash.features.tipping.R
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Custom tip-amount entry, presented as a bottom sheet over the still-visible tip card + modal.
 * Confirming commits the amount to the shared tip selection (see [TipAmountEntryViewModel]) and
 * dismisses the sheet; the underlying tip modal then reflects the chosen amount.
 */
@Composable
fun TipAmountEntryScreen() {
    val viewModel = hiltViewModel<TipAmountEntryViewModel>()
    val navigator = LocalCodeNavigator.current

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<TipAmountEntryViewModel.Event.Confirmed>()
            .collect { navigator.pop() }
    }

    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = { viewModel.dispatchEvent(TipAmountEntryViewModel.Event.ConfirmRequested) },
        onChangeCurrency = { navigator.push(AppRoute.Main.RegionSelection) },
        appBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_amountToTip),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = { AppBarDefaults.Close { navigator.hide() } },
            )
        },
    )
}
