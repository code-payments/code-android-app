package com.flipcash.app.tipping.internal.screens

import android.os.Parcelable
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
import com.flipcash.app.core.chat.NewGroupStep
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.TokenSelectionStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.app.tipping.internal.CreateGroupViewModel
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenList
import com.flipcash.app.tokens.ui.TokenListPresentation
import com.flipcash.features.tipping.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold

/**
 * Node 10127:118100 — which currency the group's balance requirement is denominated in.
 *
 * The list comes from [SelectTokenViewModel] but the *selection* does not: that view model's own
 * selected mint is the wallet's global one, and choosing a currency for a group being drafted must
 * not move the wallet off the token the user is holding. So the chosen mint is read from and written
 * to [CreateGroupViewModel]'s draft, and the list is driven with [TokenPurpose.Balance] — the one
 * purpose that isn't a `TriggersChange` and so leaves the global selection alone.
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
            selectedToken = state.mint,
            showSelections = true,
            presentation = TokenListPresentation.Default,
            onTokenSelected = { token ->
                viewModel.dispatchEvent(CreateGroupViewModel.Event.OnMintSelected(token.address))
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
