package com.flipcash.app.tipping.internal.screens

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.NewGroupStep
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.TokenSelectionStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.app.tipping.internal.CreateGroupViewModel
import com.flipcash.app.tipping.internal.GroupCurrency
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenList
import com.flipcash.app.tokens.ui.TokenListPresentation
import com.flipcash.features.tipping.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold

/**
 * Node 10127:118100 — which currency the group's balance requirement is denominated in, with the
 * All Currencies card over the token list (node 10370:997).
 *
 * The list comes from [SelectTokenViewModel] but the *selection* does not: that view model's own
 * selected mint is the wallet's global one, and choosing a currency for a group being drafted must
 * not move the wallet off the token the user is holding. So the chosen mint is read from and written
 * to [CreateGroupViewModel]'s draft, and the list is driven with [TokenPurpose.Balance] — the one
 * purpose that isn't a `TriggersChange` and so leaves the global selection alone.
 *
 * The card and the tokens are one selection: picking either replaces the other, and while All is
 * chosen no token is marked.
 */
@Composable
internal fun SelectGroupCurrencySheet(viewModel: CreateGroupViewModel) {
    val flowNavigator = rememberFlowNavigator<NewGroupStep, Parcelable>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val tokenViewModel = hiltViewModel<SelectTokenViewModel>()
    val tokenState by tokenViewModel.stateFlow.collectAsStateWithLifecycle()

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_selectCurrency),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = { AppBarDefaults.Close { flowNavigator.back() } },
            )
        },
    ) { padding ->
        TokenList(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.inset),
            tokens = tokenState.tokens,
            styling = rememberTokenBalanceRowStyling(
                // Pill balances, dividers and a radio mark per row, which is
                // TokenListPresentation.Default's treatment — the compact Sheet presentation drops
                // all three, and this sheet shows all three.
                balanceDisplayStyle = TokenBalanceStyle.Pill(),
                selectionStyle = TokenSelectionStyle.Checkbox,
            ),
            selectedToken = (state.currency as? GroupCurrency.Specific)?.mint,
            showSelections = true,
            presentation = TokenListPresentation.Default,
            header = {
                Column {
                    AllCurrenciesCard(
                        modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
                        selected = state.currency == GroupCurrency.All,
                        total = state.totalBalance,
                        amount = state.amount,
                        satisfied = state.allCurrenciesSatisfied,
                        onClick = {
                            viewModel.dispatchEvent(
                                CreateGroupViewModel.Event.OnCurrencySelected(GroupCurrency.All)
                            )
                            flowNavigator.back()
                        },
                    )

                    // A label over nothing reads as a list that failed to load.
                    if (!tokenState.tokens.isNullOrEmpty()) {
                        Text(
                            modifier = Modifier.padding(
                                start = CodeTheme.dimens.inset,
                                end = CodeTheme.dimens.inset,
                                top = CodeTheme.dimens.grid.x5,
                                bottom = CodeTheme.dimens.grid.x1,
                            ),
                            text = stringResource(R.string.title_specificCurrency),
                            style = CodeTheme.typography.textSmall,
                            color = White50,
                        )
                    }
                }
            },
            onTokenSelected = { token ->
                viewModel.dispatchEvent(
                    CreateGroupViewModel.Event.OnCurrencySelected(
                        GroupCurrency.Specific(token.address)
                    )
                )
                flowNavigator.back()
            },
        )
    }

    // The purpose is what builds the list at all — SelectTokenViewModel assembles its balances
    // inside a flatMapLatest over this event, so the default in its initial state never emits.
    LaunchedEffect(tokenViewModel) {
        tokenViewModel.dispatchEvent(
            SelectTokenViewModel.Event.OnPurposeChanged(TokenPurpose.Balance)
        )
    }
}

/**
 * The All Currencies card — node 10370:997 selected, 10372:1015 with a token picked instead, and
 * 10372:1126 when the total falls short.
 *
 * The subtitle reports the total against the amount whichever currency is selected, so a creator
 * choosing between All and a token can see what All would come to before picking it.
 */
@Composable
private fun AllCurrenciesCard(
    selected: Boolean,
    total: Fiat,
    amount: Fiat?,
    satisfied: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CardCornerRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(White05)
            .border(
                width = CodeTheme.dimens.border,
                color = if (selected) White50 else CardOutline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = CodeTheme.dimens.grid.x3,
                vertical = CodeTheme.dimens.grid.x3,
            ),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AllCurrenciesIcon()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.title_allCurrencies),
                style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CodeTheme.colors.textMain,
            )

            val totalText = total.formatted()
            Text(
                text = when {
                    amount == null -> stringResource(R.string.subtitle_allCurrenciesTotal, totalText)
                    satisfied == false -> stringResource(
                        R.string.subtitle_allCurrenciesNeeds,
                        totalText,
                        amount.formatted(rule = Fiat.FormattingRule.Truncated),
                    )
                    else -> stringResource(
                        R.string.subtitle_allCurrenciesMeets,
                        totalText,
                        amount.formatted(rule = Fiat.FormattingRule.Truncated),
                    )
                },
                style = CodeTheme.typography.caption,
                color = if (satisfied == false) CodeTheme.colors.errorText else White50,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // The token rows' own mark, so the card reads as one more option in the same list.
        Image(
            painter = painterResource(
                if (selected) R.drawable.ic_checked else R.drawable.ic_unchecked
            ),
            contentDescription = null,
        )
    }
}

/** Node 10369:999 — the coins glyph on a grey disc, shared with the form's currency row. */
@Composable
internal fun AllCurrenciesIcon(
    modifier: Modifier = Modifier,
    discSize: Dp = AllCurrenciesDiscSize,
    iconSize: Dp = AllCurrenciesIconSize,
) {
    Box(
        modifier = modifier
            .size(discSize)
            .clip(CircleShape)
            .background(White10),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_coins),
            contentDescription = null,
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.size(iconSize),
        )
    }
}

private val CardCornerRadius = 12.dp
private val CardOutline = White.copy(alpha = 0.15f)
private val AllCurrenciesDiscSize = 32.dp
private val AllCurrenciesIconSize = 20.dp
