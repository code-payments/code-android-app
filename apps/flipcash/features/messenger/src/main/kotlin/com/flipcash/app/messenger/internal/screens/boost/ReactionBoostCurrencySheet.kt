package com.flipcash.app.messenger.internal.screens.boost

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.features.messenger.R
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.TokenSelectionStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenList
import com.flipcash.app.tokens.ui.TokenListPresentation
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The "Pay with" currency picker, drawn as a sheet over the Paid Reactions sheet so the amount and
 * emoji the viewer chose are still there when it closes. Picking a currency moves the wallet's
 * selected token, which is what the Paid Reactions sheet reads its "Pay with" pill from, then
 * closes this sheet only. The chat's send sheet opens it the same way, for its amount pill.
 */
@Composable
internal fun ReactionBoostCurrencySheet() {
    // Exit through the sheet so it animates down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    CurrencyPickerContent(onPicked = { dismissSheet() })
}

/**
 * The compact "Select Currency" list. Picking moves the wallet's selected token, then calls
 * [onPicked] once the new selection has landed, so whatever reads the selected token already
 * shows it.
 */
@Composable
internal fun CurrencyPickerContent(onPicked: () -> Unit) {
    val viewModel = hiltViewModel<SelectTokenViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Sheets stop short of the system bars, so pad for them here.
            .navigationBarsPadding(),
    ) {
        Text(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(
                    top = CodeTheme.dimens.staticGrid.x5,
                    bottom = CodeTheme.dimens.staticGrid.x5,
                ),
            text = stringResource(R.string.title_selectCurrency),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )

        TokenList(
            modifier = Modifier.fillMaxWidth(),
            tokens = state.tokens,
            presentation = TokenListPresentation.Sheet,
            // The compact sheet picker's rows (node 9120:15219): plain balances, a check on the pick.
            styling = rememberTokenBalanceRowStyling(
                nameTextStyle = CodeTheme.typography.textMedium,
                balanceDisplayStyle = TokenBalanceStyle.Plain(
                    textStyle = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.Medium),
                    color = CodeTheme.colors.textSecondary,
                ),
                iconSize = CodeTheme.dimens.staticGrid.x8,
                selectionStyle = TokenSelectionStyle.Checkmark,
                contentPadding = PaddingValues(
                    vertical = CodeTheme.dimens.staticGrid.x2,
                ),
            ),
            selectedToken = state.selectedToken,
            showSelections = true,
            onTokenSelected = {
                viewModel.dispatchEvent(SelectTokenViewModel.Event.OnTokenSelected(it.address))
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(TokenPurpose.Select))
    }

    // The view model emits this once the new selection has landed, so the pill underneath already
    // shows the new currency as this sheet slides away.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OnTokenChanged>()
            .onEach { onPicked() }
            .launchIn(this)
    }
}
