package com.flipcash.app.tokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.tokens.internal.TokenSellReceiptScreen
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.features.tokens.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
internal fun SellReceiptScreen() {
    val flowNavigator = rememberFlowNavigator<SwapStep, SwapResult>()
    val viewModel = flowSharedViewModel<SwapViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            isInModal = true,
            title = stringResource(R.string.title_sellToken, state.tokenName),
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = {
                if (state.sellProgress.loading) {
                    // swallow
                } else {
                    flowNavigator.back()
                }
            }
        )

        TokenSellReceiptScreen(viewModel)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SwapViewModel.Event.OnSellSubmitted>()
            .map { it.swapId }
            .onEach { swapId ->
                flowNavigator.navigateTo(SwapStep.Processing(swapId))
            }.launchIn(this)
    }
}
