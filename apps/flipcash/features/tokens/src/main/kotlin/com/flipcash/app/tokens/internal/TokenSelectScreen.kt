package com.flipcash.app.tokens.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenBalanceStyle
import com.flipcash.app.core.ui.TokenSelectionStyle
import com.flipcash.app.core.ui.rememberTokenBalanceRowStyling
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenList
import com.flipcash.features.tokens.R
import com.getcode.theme.CodeTheme

@Composable
internal fun SelectTokenScreen(
    tokenViewModel: SelectTokenViewModel,
) {
    val state by tokenViewModel.stateFlow.collectAsStateWithLifecycle()

    SelectTokenScreenContent(state, tokenViewModel::dispatchEvent)
}

@Composable
private fun SelectTokenScreenContent(
    state: SelectTokenViewModel.State,
    dispatch: (SelectTokenViewModel.Event) -> Unit,
) {
    val tokens = remember(state.tokens) { state.tokens }

    TokenList(
        modifier = Modifier.fillMaxSize(),
        tokens = tokens,
        selectedToken = state.selectedToken,
        styling = rememberTokenBalanceRowStyling(
            balanceDisplayStyle = TokenBalanceStyle.Pill(),
            selectionStyle = when (state.purpose) {
                TokenPurpose.Balance -> TokenSelectionStyle.Chevron
                TokenPurpose.Deposit -> TokenSelectionStyle.Chevron
                is TokenPurpose.Swap -> TokenSelectionStyle.Chevron
                is TokenPurpose.LaunchFunding -> TokenSelectionStyle.Chevron
                is TokenPurpose.Select -> TokenSelectionStyle.Checkbox
                is TokenPurpose.Tip -> TokenSelectionStyle.Checkbox
                TokenPurpose.Withdraw -> TokenSelectionStyle.Chevron
            }
        ),
        showSelections = state.purpose is TokenPurpose.Select,
        showFlags = when (state.purpose) {
            is TokenPurpose.Select -> false
            is TokenPurpose.Swap -> false
            is TokenPurpose.LaunchFunding -> false
            is TokenPurpose.Tip -> false
            else -> true
        },
        enableGreaterThanAmount = atLeast@{ _, amount ->
            when (val purpose = state.purpose) {
                is TokenPurpose.LaunchFunding -> {
                    amount.nativeAmount.valueGreaterThanOrEqualTo(purpose.amount)
                }
                is TokenPurpose.Tip -> {
                    val target = purpose.amount ?: return@atLeast true
                    amount.nativeAmount.valueGreaterThanOrEqualTo(target)
                }
                else -> true
            }
        },
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
                        text = stringResource(R.string.description_noBalanceYetForBalance),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        onTokenSelected = { dispatch(SelectTokenViewModel.Event.OnTokenSelected(it.address)) }
    )
}

@Composable
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
private fun PreviewEmptyState() {
    SelectTokenScreenContent(
        state = SelectTokenViewModel.State(
            purpose = TokenPurpose.Select,
            tokens = emptyList(),
        ),
    ) {}
}