package com.flipcash.app.pools.internal.betting

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.pools.PoolBetOutcome
import com.flipcash.app.core.pools.label
import com.flipcash.app.core.ui.FlagWithFiat
import com.flipcash.features.pools.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
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
        bottomBar = { ShareBottomBar(state, dispatchEvent) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(CodeTheme.dimens.grid.x12))
            PoolTotal(state.poolTotal)
            Spacer(modifier = Modifier.height(CodeTheme.dimens.grid.x12))
            BidOptions(
                canBid = state.canSelectOutcome,
                outcomes = state.outcomes,
                totals = state.betsPerOutcome,
                selectedOutcome = state.selectedOutcome,
            ) { dispatchEvent(PoolBettingViewModel.Event.OnOutcomeSelected(it)) }
            Spacer(modifier = Modifier.height(CodeTheme.dimens.inset))
            Text(
                text = if (state.hasBoughtIn) {
                    stringResource(R.string.subtitle_poolAlreadyBet)
                } else {
                    stringResource(R.string.subtitle_poolNotYetBet)
                },
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PoolTotal(
    poolTotal: Fiat,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        FlagWithFiat(
            fiat = poolTotal,
            textStyle = CodeTheme.typography.displayMedium,
            iconSize = CodeTheme.dimens.staticGrid.x5,
        )
        Text(
            text = stringResource(R.string.subtitle_inPoolSoFar),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun BidOptions(
    canBid: Boolean,
    outcomes: List<PoolBetOutcome>,
    totals: Map<PoolBetOutcome, Fiat>,
    selectedOutcome: PoolBetOutcome?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = CodeTheme.dimens.inset),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    onOutcomeSelected: (PoolBetOutcome) -> Unit,
) {
    val possibleOutcomes by rememberUpdatedState(outcomes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .then(modifier),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        possibleOutcomes.fastForEach { item ->
            val isSelected = remember(
                item,
                selectedOutcome
            ) { item.key == selectedOutcome?.key }

            val backgroundColor by animateColorAsState(
                if (isSelected) Color.White else Color(0xFF071F10)
            )

            val borderColor by animateColorAsState(
                if (isSelected) Color.White else CodeTheme.colors.border
            )

            val choiceOptionColor by animateColorAsState(
                if (isSelected) Color.Black else Color.White.copy(0.5f)
            )

            val choiceBetTotalColor by animateColorAsState(
                if (isSelected) Color.Black else Color.White.copy(0.3f)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .rememberedClickable(enabled = canBid, onClick = { onOutcomeSelected(item) })
                    .border(
                        width = CodeTheme.dimens.border,
                        color = borderColor,
                        shape = CodeTheme.shapes.medium
                    )
                    .background(backgroundColor, CodeTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.matchParentSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = item.label,
                        style = CodeTheme.typography.displayMedium,
                        color = choiceOptionColor,
                    )

                    Text(
                        text = (totals[item] ?: Fiat.Zero).formatted(),
                        style = CodeTheme.typography.textSmall,
                        color = choiceBetTotalColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareBottomBar(
    state: PoolBettingViewModel.State,
    dispatchEvent: (PoolBettingViewModel.Event) -> Unit
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(
                bottom = CodeTheme.dimens.grid.x2
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
    ) {
        if (state.isHost && !state.isResolved) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset),
                text = stringResource(R.string.subtitle_poolHostOutcomeDisclaimer),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        CodeButton(
            onClick = { dispatchEvent(PoolBettingViewModel.Event.OnSharePool) },
            text = stringResource(R.string.action_sharePoolWithFriends),
            buttonState = ButtonState.Filled,
            modifier = Modifier
                .fillMaxWidth(),
        )
    }
}