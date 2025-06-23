package com.flipcash.app.pools.internal.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.ui.FlagWithFiat
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.pools.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.toFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.utils.network.NetworkObserverStub
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
internal fun PoolConfirmationScreen(viewModel: PoolCreateViewModel) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    PoolConfirmationScreenContent(state, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PoolCreateViewModel.Event.OnPoolCreated>()
            .map { it.id }
            .onEach { id ->
                navigator.push(
                    ScreenRegistry.get(
                        NavScreenProvider.HomeScreen.Pools.ChoiceSelection(
                            poolId = id,
                        )
                    )
                )
            }.launchIn(this)
    }
}

@Composable
private fun PoolConfirmationScreenContent(
    state: PoolCreateViewModel.State,
    dispatchEvent: (PoolCreateViewModel.Event) -> Unit
) {
    CodeScaffold(
        modifier = Modifier
            .fillMaxSize(),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                    text = stringResource(R.string.subtitle_createPoolDisclaimer),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x2),
                    buttonState = ButtonState.Filled,
                    enabled = state.creatingPool.isIdle,
                    isLoading = state.creatingPool.loading,
                    isSuccess = state.creatingPool.success,
                    text = stringResource(R.string.action_createYourPool),
                ) {
                    dispatchEvent(PoolCreateViewModel.Event.OnCreatePool)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.inset),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                modifier = Modifier
                    .fillMaxWidth(0.6f),
                text = state.nameEntryState.selectedName,
                style = CodeTheme.typography.screenTitle,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CodeTheme.dimens.grid.x6)
                    .border(
                        width = CodeTheme.dimens.border,
                        color = CodeTheme.colors.border,
                        shape = CodeTheme.shapes.medium
                    )
                    .background(Color(0xFF071F10), CodeTheme.shapes.medium)
                    .padding(
                        horizontal = CodeTheme.dimens.grid.x4,
                        vertical = CodeTheme.dimens.grid.x4
                    ),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FlagWithFiat(
                    fiat = state.bidEntryState.selectedAmount,
                    textStyle = CodeTheme.typography.displayLarge,
                    iconSize = CodeTheme.dimens.staticGrid.x6,
                )

                Text(
                    text = stringResource(R.string.subtitle_poolBuyIn),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}


private val usdToCadRate = Rate(fx = 1.37161, currency = CurrencyCode.CAD)
private val cadToUsdRate = Rate(fx = 0.72894, currency = CurrencyCode.USD)
private val rates = mapOf(
    CurrencyCode.CAD to usdToCadRate,
    CurrencyCode.USD to cadToUsdRate,
)

@Preview
@Composable
private fun Confirmation_Preview() {
    FlipcashDesignSystem {
        CompositionLocalProvider(
            LocalNetworkObserver provides NetworkObserverStub(),
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            ),
        ) {
            Box(modifier = Modifier.background(CodeTheme.colors.background)) {
                PoolConfirmationScreenContent(
                    state = PoolCreateViewModel.State(
                        nameEntryState = NameEntryState(
                            selectedName = "Will the Pacers win the NBA Finals?"
                        ),
                        bidEntryState = BidEntryState(
                            selectedAmount = 5.00.toFiat(CurrencyCode.CAD)
                        )
                    )
                ) {}
            }
        }
    }
}