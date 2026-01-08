package com.flipcash.app.tokens.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.tokens.TokenInfoViewModel
import com.flipcash.app.tokens.data.MarketTrend
import com.flipcash.app.tokens.internal.components.info.MarketCapSection
import com.flipcash.app.tokens.internal.components.info.TokenBalance
import com.flipcash.app.tokens.internal.components.info.TokenDetailsSection
import com.flipcash.app.tokens.internal.components.marketcap.generateMarketCapData
import com.flipcash.features.tokens.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.calculateEndPadding
import com.getcode.ui.utils.calculateStartPadding
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@Composable
internal fun TokenInfoScreen(viewModel: TokenInfoViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenInfoScreen(state, viewModel::dispatchEvent)
}

@Composable
private fun TokenInfoScreen(
    state: TokenInfoViewModel.State,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()
    CodeScaffold(
        bottomBar = { BottomBar(state, hazeState, dispatch) }
    ) { innerPadding ->
        Box(
            modifier = Modifier.verticalScrollStateGradient(
                listState,
                color = CodeTheme.colors.background,
                isLongGradient = true,
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(),
                        end = innerPadding.calculateEndPadding(),
                    )
                    .hazeSource(hazeState),
                state = listState,
            ) {
                item {
                    TokenBalance(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset),
                        balance = state.balance.nativeAmount,
                        appreciation = state.appreciation,
                        onClick = {
                            dispatch(
                                TokenInfoViewModel.Event.OpenScreen(
                                    AppRoute.Main.RegionSelection(kind = RegionSelectionKind.Balance)
                                )
                            )
                        }
                    )
                }

                if (!state.isCashReserve && state.cashReservesEnabled) {
                    item {
                        CodeButton(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(horizontal = CodeTheme.dimens.inset)
                                .padding(top = CodeTheme.dimens.grid.x5),
                            buttonState = ButtonState.Filled10,
                            text = stringResource(R.string.action_viewTransactionHistory),
                        ) {
                            dispatch(
                                TokenInfoViewModel.Event.OpenScreen(
                                    AppRoute.Token.Transactions(state.token?.address!!)
                                )
                            )
                        }

                        Divider(
                            modifier = Modifier.padding(
                                horizontal = CodeTheme.dimens.inset,
                                vertical = CodeTheme.dimens.grid.x5
                            ),
                            color = CodeTheme.colors.dividerVariant,
                        )
                    }
                }

                // currency info
                item {
                    if (state.isCashReserve && state.cashReservesEnabled) {
                        Text(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(horizontal = CodeTheme.dimens.inset),
                            text = stringResource(R.string.description_cashReserves),
                            style = CodeTheme.typography.textMedium,
                            color = CodeTheme.colors.textSecondary,
                        )
                    } else {
                        TokenDetailsSection(
                            modifier = Modifier
                                .fillParentMaxWidth(),
                            state = state,
                            dispatch = dispatch
                        )
                    }
                }

                if (!state.isCashReserve) {
                    // market cap
                    state.marketCap?.let { mcap ->
                        item {
                            LaunchedEffect(state.historicalMarketCapData) {
                                if (state.historicalMarketCapData.isNotEmpty()) {
                                    return@LaunchedEffect
                                }

                                // generate sample data
                                dispatch(
                                    TokenInfoViewModel.Event.OnHistoricalMarketCapDataUpdated(
                                        generateMarketCapData(
                                            mintDate = state.token!!.createdAt!!,
                                            currentMarketCap = mcap,
                                            period = state.selectedPeriod
                                        )
                                    )
                                )
                            }

                            MarketCapSection(
                                modifier = Modifier
                                    .fillParentMaxWidth(),
                                contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
                                marketCap = mcap,
                                chartEnabled = state.marketCapChartEnabled,
                                selectedPeriod = state.selectedPeriod,
                                rawHistoricalData = state.historicalMarketCapData,
                                onPeriodSelected = {
                                    dispatch(TokenInfoViewModel.Event.OnMarketCapPeriodSelected(it))
                                    // generate sample data
                                    dispatch(
                                        TokenInfoViewModel.Event.OnHistoricalMarketCapDataUpdated(
                                            generateMarketCapData(
                                                mintDate = state.token!!.createdAt!!,
                                                currentMarketCap = mcap,
                                                period = it,
                                            )
                                        )
                                    )
                                },
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(innerPadding.calculateBottomPadding())) }
            }
        }
    }
}

@Composable
private fun BottomBar(
    state: TokenInfoViewModel.State,
    hazeState: HazeState,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset)
            .navigationBarsPadding()
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(
                    containerColor = CodeTheme.colors.background.copy(0.15f)
                )
            ) {
                this.blurRadius = 20.dp
                this.progressive = HazeProgressive.LinearGradient(
                    startIntensity = 0.5f,
                    endIntensity = 1f
                )
            }
            .padding(vertical = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        if (state.isCashReserve && state.cashReservesEnabled) {
            CodeButton(
                modifier = Modifier.weight(1f),
                buttonState = ButtonState.FilledGreen,
                text = stringResource(R.string.action_withdraw),
            ) {
                dispatch(
                    TokenInfoViewModel.Event.OpenScreen(
                        AppRoute.Transfers.Withdrawal.Amount(state.mint!!)
                    )
                )
            }
        } else if (state.cashReservesEnabled) {
            CodeButton(
                modifier = Modifier.weight(1f),
                buttonState = ButtonState.FilledGreen,
                text = stringResource(R.string.action_buy),
            ) {
                dispatch(TokenInfoViewModel.Event.OpenPurchaseMethods)
            }

            if (state.canSell) {
                CodeButton(
                    modifier = Modifier
                        .weight(1f),
                    buttonState = ButtonState.Filled20,
                    text = stringResource(R.string.action_sell),
                ) {
                    dispatch(
                        TokenInfoViewModel.Event.OpenScreen(
                            AppRoute.Token.SwapTransact(
                                purpose = TokenSwapPurpose.Sell(state.token!!.address)
                            )
                        )
                    )
                }
            }
        } else {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x3)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                text = stringResource(R.string.action_give),
            ) {
                dispatch(
                    TokenInfoViewModel.Event.OpenScreen(
                        AppRoute.Main.Give(mint = state.token?.address!!)
                    )
                )
            }
        }
    }
}