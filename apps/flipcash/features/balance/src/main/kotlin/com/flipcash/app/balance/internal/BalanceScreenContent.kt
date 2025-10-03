package com.flipcash.app.balance.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.balance.internal.components.BalanceHeader
import com.flipcash.app.core.ui.TokenBalanceRow
import com.flipcash.app.onramp.AddCashRow
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.balance.R
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.base58
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient

@Composable
internal fun BalanceScreen(viewModel: BalanceViewModel) {
    val state by viewModel.stateFlow.collectAsState()

    BalanceScreenContent(
        state = state,
        dispatchEvent = viewModel::dispatchEvent
    )
}

@Composable
private fun BalanceScreenContent(
    state: BalanceViewModel.State,
    dispatchEvent: (BalanceViewModel.Event) -> Unit
) {
    Column {
        BalanceHeader(
            modifier = Modifier
                .fillMaxWidth(),
            balance = state.totalBalance
        ) {
            dispatchEvent(BalanceViewModel.Event.OpenCurrencySelection)
        }

        AddCashRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CodeTheme.dimens.inset,
                    vertical = CodeTheme.dimens.grid.x4,
                ),
            onAddCash = { dispatchEvent(BalanceViewModel.Event.OnAddCashClicked) },
            onWithdraw = { dispatchEvent(BalanceViewModel.Event.OnWithdrawClicked) },
        )

        TokenList(
            modifier = Modifier.weight(1f),
            state = state,
            dispatchEvent = dispatchEvent
        )
    }
}

@Composable
private fun TokenList(
    modifier: Modifier = Modifier,
    state: BalanceViewModel.State,
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
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        state = listState
    ) {
        if (state.balances != null && state.balances.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(bottom = CodeTheme.dimens.inset),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset),
                        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x12),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.title_tapAboveToAddCashToWallet),
                            style = CodeTheme.typography.textMedium,
                            color = CodeTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                state.balances.orEmpty(),
                key = { index, item -> item.token.address.base58() }) { index, item ->
                TokenBalanceRow(
                    modifier = Modifier.fillParentMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset),
                    tokenWithBalance = item
                ) { }

                Divider(color = CodeTheme.colors.dividerVariant)
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
                        balances = emptyList()
                    ),
                    dispatchEvent = {}
                )
            }
        }
    }
}