package com.flipcash.app.currencycreator.internal.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.app.tokens.TokenSelectScreen
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
internal fun FundingSourceSelectionScreen() {
    val selectionViewModel = hiltViewModel<SelectTokenViewModel>()
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()

    // The currency-creator flow provides its own top bar for this step, so suppress the
    // select screen's own bar to avoid stacking two.
    TokenSelectScreen(TokenPurpose.LaunchFunding(state.totalCost), showTopBar = false)

    LaunchedEffect(selectionViewModel) {
        selectionViewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
            .filter { it.fromUser }
            .map { it.mint }
            .onEach { viewModel.dispatchEvent(CurrencyCreatorViewModel.Event.ConfirmPurchase(fundedWith = it)) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CurrencyCreatorViewModel.Event.PurchaseSubmitted>()
            .map { it.swapId }
            .onEach {
                flowNavigator.navigateTo(CurrencyCreatorStep.Processing)
            }.launchIn(this)
    }
}
