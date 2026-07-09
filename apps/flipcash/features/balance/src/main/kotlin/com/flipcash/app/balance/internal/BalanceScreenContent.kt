package com.flipcash.app.balance.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.balance.internal.components.BalanceHeader
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.tokens.ui.TokenList
import com.flipcash.features.balance.R
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun BalanceScreen(
    viewModel: BalanceViewModel,
    tokenViewModel: SelectTokenViewModel,
) {
    val balanceState by viewModel.stateFlow.collectAsStateWithLifecycle()
    val tokenState by tokenViewModel.stateFlow.collectAsStateWithLifecycle()
    BalanceScreenContent(
        addMoneyUx = balanceState.depositFirstUx,
        tokenState = tokenState,
        dispatchEvent = viewModel::dispatchEvent
    )
}

@Composable
private fun BalanceScreenContent(
    addMoneyUx: Boolean = false,
    tokenState: SelectTokenViewModel.State,
    dispatchEvent: (BalanceViewModel.Event) -> Unit
) {
    Column {
        val tokens = remember(tokenState.tokens) { tokenState.tokens }
        val isEmpty = tokens.orEmpty().isEmpty()

        val headerContent: @Composable () -> Unit = {
            BalanceHeader(
                modifier = Modifier
                    .fillMaxWidth(),
                balance = tokenState.totalBalance,
                appreciation = tokenState.aggregateAppreciation,
            ) {
                dispatchEvent(BalanceViewModel.Event.OpenCurrencySelection)
            }

            Spacer(modifier = Modifier.padding(CodeTheme.dimens.grid.x2))
        }

        TokenList(
            modifier = Modifier.weight(1f),
            itemModifier = { Modifier.animateItem(fadeInSpec = null) },
            styling = rememberTokenBalanceRowStyling(),
            // When empty, the header is rendered inside the empty state (pinned
            // to the top) so the prompt can be centered against the full
            // viewport. As a separate leading list item it would offset the
            // empty state down by the header's height.
            header = if (isEmpty) null else headerContent,
            emptyState = {
                Box(modifier = Modifier.fillParentMaxSize()) {
                    Column(modifier = Modifier.align(Alignment.TopCenter)) {
                        headerContent()
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
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
                            text = if (addMoneyUx) {
                                stringResource(R.string.description_noBalanceYetForBalance)
                            } else {
                                stringResource(R.string.description_noBalanceYetDiscover)
                            },
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )

                        CodeButton(
                            onClick = {
                                dispatchEvent(BalanceViewModel.Event.PresentDepositOptions)
                            },
                            modifier = Modifier
                                .padding(top = CodeTheme.dimens.grid.x2)
                                .align(Alignment.CenterHorizontally),
                            contentPadding = PaddingValues(),
                            text = if (addMoneyUx) {
                                stringResource(R.string.action_addMoney)
                            } else {
                                stringResource(R.string.action_discoverCurrencies)
                            },
                            shape = CircleShape,
                        )
                    }
                }
            },
            pinFooter = true,
            footer = if (tokenState.totalBalance?.nativeAmount?.hasDisplayableValue == true) {
                {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x3)
                            .navigationBarsPadding(),
                        text = if (addMoneyUx) {
                            stringResource(R.string.action_addMoney)
                        } else {
                            stringResource(R.string.action_discoverCurrencies)
                        },
                        buttonState = ButtonState.Filled10,
                        onClick = {
                            if (addMoneyUx) {
                                dispatchEvent(
                                    BalanceViewModel.Event.PresentDepositOptions
                                )
                            } else {
                                dispatchEvent(
                                    BalanceViewModel.Event.OpenScreen(AppRoute.Token.Discovery)
                                )
                            }
                        }
                    )
                }
            } else null,
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
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_BalanceScreen_Empty() {
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
                addMoneyUx = true,
                tokenState = SelectTokenViewModel.State(
                    purpose = TokenPurpose.Balance,
                    tokens = emptyList()
                ),
                dispatchEvent = {}
            )
        }
    }
}