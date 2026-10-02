package com.flipcash.app.tokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.tokens.internal.explainer.MarketCapExplainerContent
import com.flipcash.app.tokens.ui.MarketCapExplainerViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun MarketCapExplainerScreen(mint: Mint) {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<MarketCapExplainerViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            onBackIconClicked = { navigator.pop() },
        )
        MarketCapExplainerContent(state = state)
    }

    LaunchedEffect(viewModel, mint) {
        viewModel.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
    }
}
