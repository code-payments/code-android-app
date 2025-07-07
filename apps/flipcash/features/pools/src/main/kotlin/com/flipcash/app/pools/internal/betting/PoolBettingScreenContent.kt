package com.flipcash.app.pools.internal.betting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.pools.internal.betting.components.BettingBottomBar
import com.flipcash.app.pools.internal.betting.components.BidOptions
import com.flipcash.app.pools.internal.betting.components.PoolTotal
import com.flipcash.app.pools.internal.betting.components.ResolutionInfo
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun PoolBettingScreen(viewModel: PoolBettingViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    PoolBettingScreenContent(state, viewModel::dispatchEvent)
}

@Composable
private fun PoolBettingScreenContent(
    state: PoolBettingViewModel.State,
    dispatchEvent: (PoolBettingViewModel.Event) -> Unit
) {
    CodeScaffold(
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth(0.6f),
                    text = state.metadata.name,
                    style = CodeTheme.typography.screenTitle,
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
            }
        },
        bottomBar = {
            if (state.isLoaded && state.isDistributed != true && !state.isResolved) {
                BettingBottomBar(state)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(CodeTheme.dimens.grid.x12))
            PoolTotal(state.poolTotal, state.isResolved, state.isDistributed == true)
            Spacer(modifier = Modifier.height(CodeTheme.dimens.grid.x12))
            BidOptions(
                canBid = state.canSelectOutcome,
                buyIn = state.metadata.buyIn,
                outcomes = state.outcomes,
                totals = state.totalPerOutcome,
                selectedOutcome = state.selectedOutcome,
                resolution = state.metadata.resolution,
                isLoaded = state.isLoaded,
            ) { dispatchEvent(PoolBettingViewModel.Event.OnOutcomeSelected(it)) }

            ResolutionInfo(state, Modifier.weight(1f))
        }
    }
}