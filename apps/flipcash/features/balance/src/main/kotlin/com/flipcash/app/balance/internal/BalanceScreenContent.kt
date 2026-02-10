package com.flipcash.app.balance.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.balance.internal.components.BalanceHeader
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.money.formatted
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.onramp.AddCashRow
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenList
import com.flipcash.features.balance.R
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable

@Composable
internal fun BalanceScreen(
    viewModel: BalanceViewModel,
    tokenViewModel: SelectTokenViewModel,
) {
    val walletState by viewModel.stateFlow.collectAsStateWithLifecycle()
    val tokenState by tokenViewModel.stateFlow.collectAsStateWithLifecycle()
    BalanceScreenContent(
        walletState = walletState,
        tokenState = tokenState,
        dispatchEvent = viewModel::dispatchEvent
    )
}

@Composable
private fun BalanceScreenContent(
    walletState: BalanceViewModel.State,
    tokenState: SelectTokenViewModel.State,
    dispatchEvent: (BalanceViewModel.Event) -> Unit
) {
    Column {
        BalanceHeader(
            modifier = Modifier
                .fillMaxWidth(),
            balance = tokenState.totalBalance,
            appreciation = tokenState.aggregateAppreciation,
        ) {
            dispatchEvent(BalanceViewModel.Event.OpenCurrencySelection)
        }

        if (walletState.quickActionsEnabled) {
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
        } else {
            Spacer(modifier = Modifier.padding(CodeTheme.dimens.grid.x2))
        }

        val tokens = remember(tokenState.tokens) { tokenState.tokens }

        TokenList(
            modifier = Modifier.weight(1f),
            reservesEnabled = tokenState.reservesEnabled,
            emptyState = {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(bottom = CodeTheme.dimens.grid.x20),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset),
                        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.title_noBalanceYet),
                            style = CodeTheme.typography.textLarge,
                            color = CodeTheme.colors.textMain,
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            modifier = Modifier.fillMaxWidth(0.6f),
                            text = stringResource(R.string.description_noBalanceYet),
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            footer = { mint, reserves ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .rememberedClickable {
                            dispatchEvent(
                                BalanceViewModel.Event.OpenScreen(
                                    AppRoute.Token.Info(mint)
                                )
                            )
                        }
                        .padding(
                            vertical = CodeTheme.dimens.grid.x3,
                            horizontal = CodeTheme.dimens.inset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.title_cashReserves),
                        style = CodeTheme.typography.screenTitle,
                        color = CodeTheme.colors.textMain,
                    )

                    Icon(
                        modifier = Modifier
                            .padding(top = CodeTheme.dimens.grid.x1)
                            .padding(start = CodeTheme.dimens.grid.x1),
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = CodeTheme.colors.textMain,
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = reserves.formatted(),
                        style = CodeTheme.typography.screenTitle,
                        color = CodeTheme.colors.textMain,
                    )
                }
            },
            tokens = tokens,
            onTokenSelected = {
                dispatchEvent(
                    BalanceViewModel.Event.OpenScreen(
                        AppRoute.Token.Info(it.address)
                    )
                )
            }
        )
    }
}

private val cadUsdRate = Rate(fx = 1.371881, currency = CurrencyCode.CAD)
private val usdCadRate = Rate(fx = 1.0 / 1.371881, currency = CurrencyCode.CAD)

@Preview
@Composable
private fun Preview_BalanceScreen_Empty() {
    FlipcashPreview {
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
                    walletState = BalanceViewModel.State(),
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