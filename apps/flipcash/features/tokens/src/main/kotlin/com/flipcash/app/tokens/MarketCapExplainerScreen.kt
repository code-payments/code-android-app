package com.flipcash.app.tokens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.tokens.internal.explainer.MarketCapExplainerContent
import com.flipcash.app.tokens.ui.MarketCapExplainerViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator

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
        when {
            state.unavailable -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.error_marketCapExplainerUnavailable),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CodeCircularProgressIndicator(color = CodeTheme.colors.textSecondary)
            }
            else -> MarketCapExplainerContent(state = state)
        }
    }

    LaunchedEffect(viewModel, mint) {
        viewModel.dispatchEvent(MarketCapExplainerViewModel.Event.OnMintProvided(mint))
    }
}
