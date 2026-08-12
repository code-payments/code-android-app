package com.flipcash.app.balance.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.flipcash.shared.transactionhistory.ActivityFeedRow
import com.getcode.theme.CodeTheme

private const val TokenStackKey = "tokenStack"

@Composable
internal fun WalletScreen(
    viewModel: WalletViewModel,
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
    balanceState: WalletViewModel.State,
    tokenState: SelectTokenViewModel.State,
    dispatchEvent: (WalletViewModel.Event) -> Unit,
) {
    val listState = rememberLazyListState()
    // Px the token stack has scrolled above the viewport top, read live so the stack collapses (then
    // releases and scrolls off) as the list scrolls. A lambda so the stack reads it in its placement
    // phase without recomposing.
    val scrolledPast = {
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == TokenStackKey }
            ?.let { -it.offset.toFloat() } ?: 0f
    }
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = CodeTheme.dimens.inset,
            start = CodeTheme.dimens.inset,
            end = CodeTheme.dimens.inset,
            bottom = LocalTabBarPadding.current.calculateBottomPadding() + CodeTheme.dimens.grid.x12,
        )
    ) {
        item {
            Spacer(Modifier.height(CodeTheme.dimens.grid.x15))
        }

        item {
            BalanceHeader(
                modifier = Modifier
                    .fillMaxWidth(),
                balance = tokenState.totalBalance,
                appreciation = tokenState.aggregateAppreciation,
            ) {
                dispatchEvent(WalletViewModel.Event.OpenCurrencySelection)
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
                            dispatchEvent(WalletViewModel.Event.PresentDepositOptions)
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
                            WalletViewModel.Event.OpenScreen(
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
                        .clickable(onClick = { dispatchEvent(WalletViewModel.Event.PresentDepositOptions) })
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

        if (balanceState.transactions.isNotEmpty()) {
            item(key = "recentHeader") {
                Text(
                    text = stringResource(R.string.title_recentActivity),
                    style = CodeTheme.typography.screenTitle,
                    color = CodeTheme.colors.textMain,
                    modifier = Modifier.padding(
                        top = CodeTheme.dimens.grid.x4,
                        bottom = CodeTheme.dimens.grid.x1,
                    ),
                )
            }
            // Preview of the most recent activity (newest first); the full history lives on its own
            // screen. The VM/coordinator already bounds this list, so just render it.
            items(
                items = balanceState.transactions,
                key = { it.id },
            ) { item ->
                ActivityFeedRow(item = item, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}