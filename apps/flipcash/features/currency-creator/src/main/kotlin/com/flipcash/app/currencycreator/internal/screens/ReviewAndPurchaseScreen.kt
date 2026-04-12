package com.flipcash.app.currencycreator.internal.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.RenderedBill
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun ReviewAndPurchaseScreen() {
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    ReviewAndPurchaseContent(state, viewModel::dispatchEvent)
}

@Composable
internal fun ReviewAndPurchaseContent(
    state: CurrencyCreatorViewModel.State,
    dispatch: (CurrencyCreatorViewModel.Event) -> Unit
) {
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()
    CodeScaffold(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset),
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        top = CodeTheme.dimens.grid.x6,
                        bottom = CodeTheme.dimens.grid.x3
                    ),
                text = stringResource(
                    R.string.action_buyFirstToCreate,
                    state.purchaseAmount.formatted(rule = Fiat.FormattingRule.Truncated)
                ),
                enabled = state.hasName,
                onClick = {

                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    top = CodeTheme.dimens.grid.x10,
                    bottom = CodeTheme.dimens.grid.x12
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x5),
        ) {
            TokenIconWithName(
                tokenName = state.nameFieldState.text.toString(),
                tokenImage = state.icon.dataOrNull,
                imageSize = CodeTheme.dimens.staticGrid.x6,
                textStyle = CodeTheme.typography.displaySmall,
                spacing = CodeTheme.dimens.grid.x2,
            )

            AnimatedContent(
                modifier = Modifier
                    .padding(top = CodeTheme.dimens.grid.x3)
                    .fillMaxWidth()
                    .weight(1f),
                targetState = state.bill,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it?.data },
            ) { bill ->
                if (bill != null) {
                    RenderedBill(
                        modifier = Modifier.weight(1f),
                        bill = bill,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
