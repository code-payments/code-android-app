package com.flipcash.app.balance.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.balance.internal.components.BalanceHeader
import com.flipcash.app.balance.internal.components.FeedItem
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.app.theme.FlipcashDesignSystem
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.toFiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlinx.coroutines.flow.flowOf
import kotlin.collections.emptyList

@Composable
internal fun BalanceScreen(viewModel: BalanceViewModel) {
    val state by viewModel.stateFlow.collectAsState()
    val feed = viewModel.feed.collectAsLazyPagingItems()

    BalanceScreenContent(
        state = state,
        feed = feed,
        dispatchEvent = viewModel::dispatchEvent
    )
}

@Composable
private fun BalanceScreenContent(
    state: BalanceViewModel.State,
    feed: LazyPagingItems<ActivityFeedMessage>,
    dispatchEvent: (BalanceViewModel.Event) -> Unit
) {
    Column {
        BalanceHeader(
            modifier = Modifier
                .fillMaxWidth(),
            balance = state.balance
        ) {
            dispatchEvent(BalanceViewModel.Event.OpenCurrencySelection)
        }

        FeedList(
            modifier = Modifier.weight(1f),
            state = state,
            feed = feed,
            dispatchEvent = dispatchEvent
        )
    }
}

@Composable
private fun FeedList(
    modifier: Modifier = Modifier,
    state: BalanceViewModel.State,
    feed: LazyPagingItems<ActivityFeedMessage>,
    dispatchEvent: (BalanceViewModel.Event) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier
            .verticalScrollStateGradient(
                scrollState = listState,
                color = CodeTheme.colors.background,
                showAtEnd = true
            ),
        state = listState
    ) {
        if (feed.itemCount == 0 && feed.loadState.append is LoadState.NotLoading) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize().padding(bottom = CodeTheme.dimens.inset),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = CodeTheme.dimens.inset),
                        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6)
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = "Ask a friend to give you some Flipcash, or deposit USDC from your crypto exchange or other crypto wallet",
                            style = CodeTheme.typography.textMedium,
                            color = CodeTheme.colors.textMain,
                            textAlign = TextAlign.Center,
                        )

                        CodeButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = "Deposit USDC",
                            buttonState = ButtonState.Filled
                        ) {
                            dispatchEvent(BalanceViewModel.Event.OpenDeposit)
                        }
                    }
                }
            }
        } else {
            items(feed.itemCount, key = feed.itemKey { item -> item.id }) { index ->
                val message = feed[index] ?: return@items
                FeedItem(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .animateItem(),
                    message = message,
                    canViewDetails = state.canViewDetails,
                    isExpanded = state.expandedItem == message.id,
                    dispatch = dispatchEvent
                )

                if (index < feed.itemCount - 1) {
                    Divider(color = CodeTheme.colors.dividerVariant)
                }
            }

            item {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}

private val cadUsdRate = Rate(fx = 1.371881, currency = CurrencyCode.CAD)
private val usdCadRate = Rate(fx = 1.0 / 1.371881, currency = CurrencyCode.CAD)

@Preview
@Composable
private fun Preview_BalanceScreen_Empty() {
    FlipcashDesignSystem {
        CompositionLocalProvider(
            LocalExchange provides ExchangeStub(
                providedRates = mapOf(
                    CurrencyCode.CAD to cadUsdRate,
                    CurrencyCode.USD to usdCadRate
                ),
                context = LocalContext.current
            ),
        ) {
            Box(modifier = Modifier.background(CodeTheme.colors.background)) {
                BalanceScreenContent(
                    state = BalanceViewModel.State(
                        balance = LocalFiat(0.toFiat())
                    ),
                    feed = flowOf(PagingData.empty<ActivityFeedMessage>()).collectAsLazyPagingItems(),
                    dispatchEvent = {}
                )
            }
        }
    }
}

@Preview
@Composable
private fun Preview_FeedList_Empty() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            FeedList(
                state = BalanceViewModel.State(
                    balance = LocalFiat(0.toFiat())
                ),
                feed = flowOf(PagingData.empty<ActivityFeedMessage>()).collectAsLazyPagingItems()
            ) { }
        }
    }
}