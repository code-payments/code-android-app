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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.theme.FlipcashPreview
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
        showSelections = state.purpose is TokenPurpose.Select,
        showFlags = state.purpose !is TokenPurpose.Select,
        includeReserves = state.purpose is TokenPurpose.Deposit || state.purpose is TokenPurpose.Withdraw,
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
                        text = stringResource(R.string.description_noBalanceYet),
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
private fun PreviewEmptyState() {
    FlipcashPreview(showBackground = true) {
        SelectTokenScreenContent(
            state = SelectTokenViewModel.State(
                purpose = TokenPurpose.Select,
                tokens = emptyList(),
            ),
        ) {

        }
    }
}