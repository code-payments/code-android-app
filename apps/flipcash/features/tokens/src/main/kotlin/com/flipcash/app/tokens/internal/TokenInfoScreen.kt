package com.flipcash.app.tokens.internal

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.toYesOrNo
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.app.tokens.TokenInfoViewModel
import com.flipcash.features.tokens.R
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.bolded
import com.getcode.theme.extraSmall
import com.getcode.ui.components.CodeChip
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.components.text.ExpandableText
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold

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
    CodeScaffold(
        bottomBar = {
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
    ) { innerPadding ->
        Box(
            modifier = Modifier.verticalScrollStateGradient(
                listState,
                color = CodeTheme.colors.background
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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

                item {
                    CodeButton(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(
                                top = CodeTheme.dimens.grid.x5,
                            ),
                        buttonState = ButtonState.Filled10,
                        text = stringResource(R.string.action_transactionHistory),
                    ) {

                    }

                    Divider(
                        modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x5),
                        color = CodeTheme.colors.dividerVariant,
                    )
                }

                // market cap
                state.marketCap?.let { mcap ->
                    item {
                        MarketCapSection(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(horizontal = CodeTheme.dimens.inset),
                            marketCap = mcap
                        )
                    }
                }

                // currency info
                item {
                    CurrencyInfoSection(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset),
                        state = state,
                        dispatch = dispatch
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenBalance(
    modifier: Modifier = Modifier,
    balance: Fiat?,
    appreciation: Fiat,
    onClick: () -> Unit
) {
    val exchange = LocalExchange.current
    Column(
        modifier = modifier
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(
                top = CodeTheme.dimens.grid.x9,
                bottom = CodeTheme.dimens.grid.x9,
            ),
    ) {
        if (balance == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CodeCircularProgressIndicator()
            }
        } else {
            Crossfade(balance) { amount ->
                AmountArea(
                    amountText = amount.formatted(),
                    isAltCaption = false,
                    isAltCaptionKinIcon = false,
                    captionText = null,
                    currencyResId = exchange.getFlagByCurrency(amount.currencyCode.name),
                    isClickable = true,
                    textStyle = CodeTheme.typography.displayLarge,
                    onClick = onClick
                )
            }

            if (appreciation > Fiat.Zero) {
                Crossfade(appreciation) { amount ->
                    val relativeAmount = remember(amount) {
                        val prefix = if (amount.isNegative) "-" else "+"
                        val value = amount.formatted()
                        "$prefix$value"
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                    ) {
                        CodeChip(
                            shape = CodeTheme.shapes.extraSmall,
                            label = relativeAmount,
                            contentPadding = PaddingValues(
                                horizontal = 4.dp,
                                vertical = 2.dp,
                            ),
                            backgroundColor = CodeTheme.colors.surfaceSuccess,
                            contentColor = CodeTheme.colors.successText,
                        )
                        Text(
                            text = "from currency appreciation",
                            style = CodeTheme.typography.textSmall.bolded(),
                            color = CodeTheme.colors.successText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyInfoSection(
    modifier: Modifier = Modifier,
    state: TokenInfoViewModel.State,
    dispatch: (TokenInfoViewModel.Event) -> Unit
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Icon(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                painter = painterResource(R.drawable.ic_info_bars),
                contentDescription = null,
            )

            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.subtitle_currencyInfo),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
        }

        ExpandableText(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
            text = state.token?.description.orEmpty(),
            style = CodeTheme.typography.textMedium,
            isExpanded = state.descriptionExpanded,
            contentPadding = PaddingValues(horizontal = CodeTheme.dimens.inset)
        ) {
            dispatch(TokenInfoViewModel.Event.ExpandDescription(!state.descriptionExpanded))
        }
    }
}

@Composable
private fun MarketCapSection(
    modifier: Modifier = Modifier,
    marketCap: Fiat,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.subtitle_marketCap),
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textSecondary,
        )
        Text(
            text = marketCap.formatted(),
            style = CodeTheme.typography.displaySmall,
            color = CodeTheme.colors.textMain,
        )

        Divider(
            modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x5),
            color = CodeTheme.colors.dividerVariant,
        )
    }
}

@Composable
@Preview
private fun PreviewTokenInfo() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            ExpandableText(
                modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                text = LoremIpsum(words = 400).values.joinToString(" "),
                contentPadding = PaddingValues(horizontal = 16.dp),
                style = CodeTheme.typography.textMedium,
                isExpanded = false,
                onToggle = { }
            )
        }
    }
}