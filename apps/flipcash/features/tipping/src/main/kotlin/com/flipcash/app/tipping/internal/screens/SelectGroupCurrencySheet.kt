package com.flipcash.app.tipping.internal.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.TokenSelectionStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.app.tipping.internal.CreateGroupViewModel
import com.flipcash.app.tipping.internal.communityCurrencies
import com.flipcash.app.tipping.internal.withoutUsdf
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenList
import com.flipcash.app.tokens.ui.TokenListPresentation
import com.flipcash.features.tipping.R
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold

/**
 * Node 10127:118100 — which currency the group's balance requirement is denominated in: the token
 * list, highest balance first. Its top row is the form's default ([communityCurrencies]).
 *
 * The list comes from [SelectTokenViewModel] but the *selection* does not: that view model's own
 * selected mint is the wallet's global one, and choosing a currency for a group being drafted must
 * not move the wallet off the token the user is holding. So the chosen mint is read from and written
 * to [CreateGroupViewModel]'s draft, and the list is driven with [TokenPurpose.Balance] — the one
 * purpose that isn't a `TriggersChange` and so leaves the global selection alone.
 *
 * USDF is left out of the list: dollars aren't a community currency.
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
            tokens = tokenState.tokens?.withoutUsdf { it.token.address },
            styling = rowStyling,
            selectedToken = state.currency,
            showSelections = true,
            presentation = TokenListPresentation.Default,
            // The last row clears the gesture bar when scrolled to the end.
            footer = { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) },
            onTokenSelected = { token ->
                viewModel.dispatchEvent(
                    CreateGroupViewModel.Event.OnCurrencySelected(token.address)
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
