package com.flipcash.app.balance.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.balance.internal.components.BalanceHeader
import com.flipcash.app.onramp.AddCashRow
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.app.tokens.SelectTokenViewModel
import com.flipcash.app.tokens.TokenList
import com.flipcash.app.tokens.TokenPurpose
import com.flipcash.features.balance.R
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.theme.CodeTheme

@Composable
internal fun BalanceScreen(
    viewModel: BalanceViewModel,
    tokenViewModel: SelectTokenViewModel,
) {
    val tokenState by tokenViewModel.stateFlow.collectAsState()
    BalanceScreenContent(
        tokenState = tokenState,
        dispatchEvent = viewModel::dispatchEvent
    )
}

@Composable
private fun BalanceScreenContent(
    tokenState: SelectTokenViewModel.State,
    dispatchEvent: (BalanceViewModel.Event) -> Unit
) {
    Column {
        BalanceHeader(
            modifier = Modifier
                .fillMaxWidth(),
            balance = tokenState.totalBalance
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

        val tokens = remember(tokenState.tokens) { tokenState.tokens }

        TokenList(
            modifier = Modifier.weight(1f),
            emptyState = {
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
            },
            tokens = tokens,
        )
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
                    tokenState = SelectTokenViewModel.State(
                        purpose = TokenPurpose.Balance,
                        tokens = emptyList()
                    ),
                    dispatchEvent = {}
                )
            }
        }
    }
}