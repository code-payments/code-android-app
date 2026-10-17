package com.flipcash.app.tipping.internal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenBalanceRowStyling
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.TokenSelectionStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.app.tipping.internal.CreateGroupViewModel
import com.flipcash.app.tipping.internal.GroupCurrency
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenList
import com.flipcash.app.tokens.ui.TokenListPresentation
import com.flipcash.features.tipping.R
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold

/**
 * Node 10127:118100 — which currency the group's balance requirement is denominated in: the token
 * list with an "Any Community Currency" entry after the last token.
 *
 * The list comes from [SelectTokenViewModel] but the *selection* does not: that view model's own
 * selected mint is the wallet's global one, and choosing a currency for a group being drafted must
 * not move the wallet off the token the user is holding. So the chosen mint is read from and written
 * to [CreateGroupViewModel]'s draft, and the list is driven with [TokenPurpose.Balance] — the one
 * purpose that isn't a `TriggersChange` and so leaves the global selection alone.
 *
 * The entry and the tokens are one selection: picking either replaces the other, and while All is
 * chosen no token is marked. USDF stays in the list; the contract can't say "community only".
 */
@Composable
internal fun SelectGroupCurrencySheet(viewModel: CreateGroupViewModel) {
    // Exit through the sheet so it animates down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val tokenViewModel = hiltViewModel<SelectTokenViewModel>()
    val tokenState by tokenViewModel.stateFlow.collectAsStateWithLifecycle()

    val rowStyling = rememberTokenBalanceRowStyling(
        // Pill balances, dividers and a radio mark per row, which is
        // TokenListPresentation.Default's treatment — the compact Sheet presentation drops
        // all three, and this sheet shows all three.
        balanceDisplayStyle = TokenBalanceStyle.Pill(),
        selectionStyle = TokenSelectionStyle.Checkbox,
    )

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_selectCurrency),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = { AppBarDefaults.Close(onClick = dismissSheet) },
            )
        },
    ) { padding ->
        TokenList(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            tokens = tokenState.tokens,
            styling = rowStyling,
            selectedToken = (state.currency as? GroupCurrency.Specific)?.mint,
            showSelections = true,
            presentation = TokenListPresentation.Default,
            footer = {
                AnyCurrencyRow(
                    selected = state.currency == GroupCurrency.All,
                    styling = rowStyling,
                    onClick = {
                        viewModel.dispatchEvent(
                            CreateGroupViewModel.Event.OnCurrencySelected(GroupCurrency.All)
                        )
                        dismissSheet()
                    },
                )
            },
            onTokenSelected = { token ->
                viewModel.dispatchEvent(
                    CreateGroupViewModel.Event.OnCurrencySelected(
                        GroupCurrency.Specific(token.address)
                    )
                )
                dismissSheet()
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
 * The "Any Community Currency" entry, last in the list. It is the same radio selection as the token
 * rows above it, so it takes its metrics from the same [TokenBalanceRowStyling] [TokenList] hands
 * them (name style, icon size, vertical padding, check mark) and keeps the divider the rows have.
 * The navigation-bar inset sits below it so the last row clears the gesture bar when scrolled to
 * the end.
 */
@Composable
private fun AnyCurrencyRow(
    selected: Boolean,
    styling: TokenBalanceRowStyling,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(styling.contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AllCurrenciesIcon(
                    discSize = styling.iconSize,
                    iconSize = styling.iconSize * (AllCurrenciesIconSize / AllCurrenciesDiscSize),
                )

                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.title_anyCommunityCurrency),
                    style = styling.nameTextStyle,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Image(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(start = CodeTheme.dimens.grid.x3),
                painter = painterResource(
                    if (selected) R.drawable.ic_checked else R.drawable.ic_unchecked
                ),
                contentDescription = null,
            )
        }

        HorizontalDivider(color = CodeTheme.colors.dividerVariant)
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
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

private val AllCurrenciesDiscSize = 32.dp
private val AllCurrenciesIconSize = 20.dp
