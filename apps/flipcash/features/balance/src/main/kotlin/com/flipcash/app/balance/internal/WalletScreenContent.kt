package com.flipcash.app.balance.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.TokenCardStack
import com.flipcash.app.balance.internal.components.BalanceHeader
import com.flipcash.app.balance.internal.components.OnboardingFunnel
import com.flipcash.app.balance.internal.components.OnboardingItem
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.features.balance.R
import com.getcode.theme.CodeTheme

private const val TokenStackKey = "tokenStack"

@Composable
internal fun WalletScreen(
    viewModel: BalanceViewModel,
    tokenViewModel: SelectTokenViewModel,
) {
    val balanceState by viewModel.stateFlow.collectAsStateWithLifecycle()
    val tokenState by tokenViewModel.stateFlow.collectAsStateWithLifecycle()
    WalletScreenContent(
        balanceState = balanceState,
        tokenState = tokenState,
        dispatchEvent = viewModel::dispatchEvent
    )
}

@Composable
internal fun WalletScreenContent(
    balanceState: BalanceViewModel.State,
    tokenState: SelectTokenViewModel.State,
    dispatchEvent: (BalanceViewModel.Event) -> Unit
) {
    val listState = rememberLazyListState()
    // Sticky per-card collapse: the fan scrolls normally (the stack keeps a fixed fanned height, so
    // scrolling is stable) and each card pins to the top as it scrolls above the viewport, building a
    // deck while the cards below stay fanned and readable. `scrolledPast` = px of the stack scrolled
    // above the viewport top, read live so the stack re-lays-out its cards as the list scrolls.
    // May be negative when the stack sits below the pin line (i.e. scrolled to the top) so cards
    // fan flush there instead of staying stuck under the pin inset.
    val scrolledPast = {
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == TokenStackKey }
            ?.let { -it.offset.toFloat() } ?: 0f
    }
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CodeTheme.dimens.inset,
            end = CodeTheme.dimens.inset,
            bottom = LocalTabBarPadding.current.calculateBottomPadding(),
        )
    ) {
        item {
            Spacer(Modifier.height(CodeTheme.dimens.grid.x20))
        }

        item {
            BalanceHeader(
                modifier = Modifier
                    .fillMaxWidth(),
                balance = tokenState.totalBalance,
                appreciation = tokenState.aggregateAppreciation,
            ) {
                dispatchEvent(BalanceViewModel.Event.OpenCurrencySelection)
            }
        }

        item {
            Spacer(Modifier.height(CodeTheme.dimens.grid.x6))
        }

        if (!balanceState.isOnboardingComplete) {
            item {
                OnboardingFunnel(
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = CodeTheme.dimens.grid.x5),
                    title = stringResource(R.string.title_tipOnboarding),
                    items = balanceState.onboardingItems,
                ) { item ->
                    when (item) {
                        is OnboardingItem.AddMoney -> {
                            dispatchEvent(BalanceViewModel.Event.PresentDepositOptions)
                        }
                        is OnboardingItem.ScanTipCard -> {

                        }
                    }
                }
            }
        }

        tokenState.tokens?.takeIf { it.isNotEmpty() }?.let { tokens ->
            item(key = TokenStackKey) {
                TokenCardStack(
                    tokens = tokens,
                    modifier = Modifier.fillMaxWidth(),
                    pinInset = statusBarInset,
                    scrolledPast = scrolledPast,
                    onCardClick = { token ->
                        dispatchEvent(
                            BalanceViewModel.Event.OpenScreen(
                                AppRoute.Token.Info(mint = token.token.address)
                            )
                        )
                    },
                )
            }
        }

        if (balanceState.hasAddedMoney) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { dispatchEvent(BalanceViewModel.Event.PresentDepositOptions) })
                        .padding(vertical = CodeTheme.dimens.inset),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                    ) {
                        Icon(
                            painter = rememberVectorPainter(Icons.Outlined.AddCircleOutline),
                            contentDescription = null,
                            tint = CodeTheme.colors.textSecondary,
                        )
                        Text(
                            text = stringResource(R.string.action_addMoney),
                            style = CodeTheme.typography.textMedium,
                            color = CodeTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}