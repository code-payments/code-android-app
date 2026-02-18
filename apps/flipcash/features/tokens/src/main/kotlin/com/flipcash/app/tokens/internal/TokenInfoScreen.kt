package com.flipcash.app.tokens.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import com.flipcash.app.analytics.Action
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.tokens.ui.TokenInfoViewModel
import com.flipcash.app.tokens.internal.components.info.MarketCapSection
import com.flipcash.app.tokens.internal.components.info.TokenBalance
import com.flipcash.app.tokens.internal.components.info.TokenDetailsSection
import com.flipcash.features.tokens.R
import com.getcode.libs.analytics.LocalAnalytics
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

@Composable
internal fun TokenInfoScreen(viewModel: TokenInfoViewModel, isForNeededFunds: Boolean) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    TokenInfoScreen(isForNeededFunds, state, viewModel::dispatchEvent)
}

@Composable
private fun TokenInfoScreen(
    isForNeededFunds: Boolean,
    state: TokenInfoViewModel.State,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    val listState = rememberLazyListState()

    CodeScaffold(
        bottomBar = { BottomBar(isForNeededFunds, state, dispatch) }
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
                                            AppRoute.Main.RegionSelection(kind = RegionSelectionKind.Balance)
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
                            if (state.isCashReserve && state.cashReservesEnabled) {
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
                                val loadable = state.historicalMarketCapData[state.selectedPeriod] ?: Loadable.Loaded(emptyList())
                                item {
                                    MarketCapSection(
                                        modifier = Modifier
                                            .fillParentMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset),
                                        marketCap = mcap,
                                        chartEnabled = state.marketCapChartEnabled,
                                        selectedPeriod = state.selectedPeriod,
                                        rawHistoricalData = loadable,
                                        onPeriodSelected = {
                                            dispatch(TokenInfoViewModel.Event.OnMarketCapPeriodSelected(it))
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
    isForNeededFunds: Boolean,
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
            isForNeededFunds = isForNeededFunds,
            state = state,
            dispatch = dispatch
        )
    }
}

@Composable
private fun BottomBarButtons(
    isForNeededFunds: Boolean,
    state: TokenInfoViewModel.State,
    modifier: Modifier = Modifier,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    val analytics = LocalAnalytics.current as FlipcashAnalyticsService
    when (val loadable = state.token) {
        is Loadable.Error -> Unit
        is Loadable.Loaded -> {
            Row(
                modifier = modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                if (!state.isCashReserve) {
                    if (state.cashReservesEnabled) {
                        CodeButton(
                            modifier = Modifier.weight(1f),
                            buttonState = ButtonState.Filled,
                            text = stringResource(R.string.action_buy),
                        ) {
                            dispatch(TokenInfoViewModel.Event.OpenPurchaseMethods(forNeededFunds = isForNeededFunds))
                        }

                        if (state.canSell) {
                            CodeButton(
                                modifier = Modifier
                                    .weight(1f),
                                buttonState = ButtonState.Filled20,
                                text = stringResource(R.string.action_sell),
                            ) {
                                analytics.action(Action.Sell)
                                dispatch(
                                    TokenInfoViewModel.Event.OpenScreen(
                                        AppRoute.Token.SwapTransact(
                                            purpose = TokenSwapPurpose.Sell(loadable.data.address),
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
                                    AppRoute.Main.Give(mint = loadable.data.address)
                                )
                            )
                        }
                    }
                }
            }
        }
        is Loadable.Loading -> Unit
    }
}