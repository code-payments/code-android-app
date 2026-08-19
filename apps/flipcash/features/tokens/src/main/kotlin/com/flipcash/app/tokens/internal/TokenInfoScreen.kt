package com.flipcash.app.tokens.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.tokens.internal.components.info.CurrencyInfoContentV2
import com.flipcash.app.tokens.internal.components.info.MarketCapSection
import com.flipcash.app.tokens.internal.components.info.TokenBalance
import com.flipcash.app.tokens.internal.components.info.TokenDetailsSection
import com.flipcash.app.tokens.ui.TokenInfoViewModel
import com.flipcash.features.tokens.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.drawWithGradient
import com.getcode.ui.core.measured
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.calculateEndPadding
import com.getcode.ui.utils.calculateStartPadding
import com.getcode.ui.utils.sheetResignmentBehavior
import dev.chrisbanes.haze.HazeState

@Composable
internal fun TokenInfoScreen(
    viewModel: TokenInfoViewModel,
    shortfall: Fiat?,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    hazeState: HazeState? = null,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenInfoScreen(shortfall, state, listState, contentPadding, hazeState, viewModel::dispatchEvent)
}

@Composable
private fun TokenInfoScreen(
    shortfall: Fiat?,
    state: TokenInfoViewModel.State,
    listState: LazyListState,
    contentPadding: PaddingValues,
    hazeState: HazeState?,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    val features = LocalFeatureFlags.current
    // Collect rather than snapshot `.value` — the flow is seeded with the flag's default until
    // DataStore emits, so a remembered read freezes the default (see MenuScreenContent).
    val isNewUi by features.observe(FeatureFlag.NewUi).collectAsStateWithLifecycle()

    if (isNewUi) {
        // v2 hosts its own overlaid app bar (see the outer TokenInfoScreen); content fills behind it,
        // marked as the haze source so the frosted bar chrome frosts it, and inset by [contentPadding].
        CurrencyInfoContentV2(
            shortfall = shortfall,
            state = state,
            listState = listState,
            contentPadding = contentPadding,
            hazeState = hazeState,
            dispatch = dispatch,
        )
        return
    }

    CodeScaffold(
        bottomBar = { BottomBar(shortfall, state, dispatch) }
    ) { innerPadding ->
        Box(
            modifier = Modifier.verticalScrollStateGradient(
                listState,
                color = CodeTheme.colors.background,
                isLongGradient = true,
                showAtEnd = false,
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(),
                        end = innerPadding.calculateEndPadding(),
                    )
                    .sheetResignmentBehavior(listState),
                state = listState,
            ) {
                when (val loadable = state.token) {
                    is Loadable.Loading -> {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize(0.24f)
                                        .aspectRatio(1f)
                                        .align(Alignment.Center),
                                ) {
                                    CodeCircularProgressIndicator(
                                        modifier = Modifier
                                            .matchParentSize(),
                                        strokeWidth = CodeTheme.dimens.grid.x1,
                                        color = Color.White,
                                        backgroundColor = Color.White.copy(0.30f),
                                        strokeCap = StrokeCap.Butt,
                                    )
                                }
                            }
                        }
                    }

                    is Loadable.Error -> {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize(0.24f)
                                        .aspectRatio(1f)
                                        .align(Alignment.Center),
                                ) {
                                    Image(
                                        modifier = Modifier
                                            .matchParentSize(),
                                        painter = painterResource(R.drawable.ic_circle_exclamation_large),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }

                    is Loadable.Loaded -> {
                        item {
                            TokenBalance(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = CodeTheme.dimens.inset),
                                balance = state.balance.nativeAmount,
                                appreciation = state.appreciation?.nativeAmount?.takeIf { state.showAppreciation },
                                onClick = {
                                    dispatch(
                                        TokenInfoViewModel.Event.OpenScreen(
                                            AppRoute.Main.RegionSelection
                                        )
                                    )
                                }
                            )
                        }

                        if (!state.isCashReserve && state.showTransactionHistory) {
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
                                            AppRoute.Token.Transactions(loadable.data.address)
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
                            if (state.isCashReserve) {
                                Text(
                                    modifier = Modifier
                                        .fillParentMaxWidth()
                                        .padding(horizontal = CodeTheme.dimens.inset),
                                    text = loadable.data.description,
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
                                val loadable = state.historicalMarketCapData[state.selectedPeriod]
                                    ?: Loadable.Loaded(emptyList())
                                item {
                                    MarketCapSection(
                                        modifier = Modifier
                                            .fillParentMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
                                        marketCap = mcap,
                                        selectedPeriod = state.selectedPeriod,
                                        rawHistoricalData = loadable,
                                        onRetry = {
                                            dispatch(
                                                TokenInfoViewModel.Event.LoadHistoricalDataForPeriod(
                                                    state.selectedPeriod
                                                )
                                            )
                                        },
                                        onPeriodSelected = {
                                            dispatch(
                                                TokenInfoViewModel.Event.OnMarketCapPeriodSelected(
                                                    it
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
    }
}

@Composable
private fun BottomBar(
    shortfall: Fiat?,
    state: TokenInfoViewModel.State,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    var buttonHeight by remember { mutableStateOf(0.dp) }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight)
                .drawWithGradient(
                    color = CodeTheme.colors.background,
                    startY = { 0f },
                    endY = { size.height * 0.38f }
                )
        )
        BottomBarButtons(
            modifier = Modifier
                .measured { buttonHeight = it.height }
                .navigationBarsPadding()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(
                    top = CodeTheme.dimens.grid.x9,
                    bottom = CodeTheme.dimens.grid.x3
                ),
            shortfall = shortfall,
            state = state,
            dispatch = dispatch
        )
    }
}

@Composable
private fun BottomBarButtons(
    shortfall: Fiat?,
    state: TokenInfoViewModel.State,
    modifier: Modifier = Modifier,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    val analytics = rememberAnalytics()
    when (val loadable = state.token) {
        is Loadable.Error -> Unit
        is Loadable.Loaded -> {
            Row(
                modifier = modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                if (state.isCashReserve) {
                    ReserveButtonOptions(
                        state = state,
                        dispatch = dispatch,
                    )
                } else {
                    ButtonOptions(
                        analytics = analytics,
                        mint = loadable.data.address,
                        shortfall = shortfall,
                        state = state,
                        dispatch = dispatch,
                    )
                }
            }
        }

        is Loadable.Loading -> Unit
    }
}

@Composable
private fun RowScope.ReserveButtonOptions(
    state: TokenInfoViewModel.State,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    val hasBalance = state.balance.nativeAmount.isPositive

    if (hasBalance) {
        // USDF/Dollars is only giveable in the new UI (v2 currency-info tiles); the legacy reserve
        // layout offers Withdraw + Deposit only.
        CodeButton(
            modifier = Modifier.weight(1f),
            buttonState = ButtonState.Filled20,
            text = stringResource(R.string.action_withdraw),
        ) {
            dispatch(
                TokenInfoViewModel.Event.OpenScreen(
                    AppRoute.Transfers.Withdrawal(showOtherOptions = false)
                )
            )
        }
    }

    CodeButton(
        modifier = Modifier.weight(1f),
        buttonState = ButtonState.Filled20,
        text = stringResource(R.string.action_deposit),
    ) {
        dispatch(TokenInfoViewModel.Event.PresentDepositOptions)
    }
}

@Composable
private fun RowScope.ButtonOptions(
    analytics: FlipcashAnalyticsService,
    mint: Mint,
    shortfall: Fiat?,
    state: TokenInfoViewModel.State,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    val canGive = state.balance.nativeAmount.isPositive

    CodeButton(
        modifier = Modifier.weight(1f),
        buttonState = ButtonState.Filled,
        text = stringResource(R.string.action_buy),
    ) {
        dispatch(TokenInfoViewModel.Event.OnBuy(shortfall))
    }

    if (canGive) {
        CodeButton(
            modifier = Modifier.weight(1f),
            buttonState = ButtonState.Filled20,
            text = stringResource(R.string.action_give),
        ) {
            dispatch(
                TokenInfoViewModel.Event.OpenScreen(
                    AppRoute.Sheets.Give(mint = mint, fromTokenInfo = true)
                )
            )
        }
    }

    if (state.canSell) {
        CodeButton(
            modifier = Modifier
                .weight(1f),
            buttonState = ButtonState.Filled20,
            text = stringResource(R.string.action_sell),
        ) {
            analytics.buttonTapped(Button.TokenSell)
            dispatch(
                TokenInfoViewModel.Event.OpenScreen(
                    AppRoute.Token.Swap(
                        purpose = SwapPurpose.Sell(mint),
                    )
                )
            )
        }
    }
}