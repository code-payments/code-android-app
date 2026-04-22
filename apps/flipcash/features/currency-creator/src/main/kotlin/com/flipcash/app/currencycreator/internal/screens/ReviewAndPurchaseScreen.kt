package com.flipcash.app.currencycreator.internal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.RenderedBill
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.core.ui.transitions.SharedTransition
import com.flipcash.app.core.ui.transitions.sharedBoundsTransition
import com.flipcash.app.currencycreator.internal.CurrencyCreatorViewModel
import com.flipcash.core.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
internal fun ReviewAndPurchaseScreen() {
    val flowNavigator = rememberFlowNavigator<CurrencyCreatorStep, CurrencyCreatorResult>()
    val viewModel = flowSharedViewModel<CurrencyCreatorViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    ReviewAndPurchaseContent(state, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<CurrencyCreatorViewModel.Event.PurchaseSubmitted>()
            .map { it.swapId }
            .onEach {
                flowNavigator.navigateTo(CurrencyCreatorStep.Processing)
            }.launchIn(this)
    }
}

@Composable
internal fun ReviewAndPurchaseContent(
    state: CurrencyCreatorViewModel.State,
    dispatch: (CurrencyCreatorViewModel.Event) -> Unit
) {
    CodeScaffold(
        modifier = Modifier
            .padding(horizontal = CodeTheme.dimens.inset),
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        bottom = CodeTheme.dimens.grid.x3
                    ),
                text = stringResource(
                    R.string.action_buyFirstToCreate,
                    state.totalCost.formatted(
                        rule = Fiat.FormattingRule.Truncated,
                        suffix = stringResource(R.string.subtitle_usdSuffix)
                    )
                ),
                enabled = state.hasName && state.processingState.isIdle,
                isLoading = state.processingState.loading,
                isSuccess = state.processingState.success,
                onClick = {
                    dispatch(CurrencyCreatorViewModel.Event.Purchase)
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
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.grid.x2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                TokenIconWithName(
                    tokenName = state.nameFieldState.text.toString(),
                    tokenImage = state.icon.dataOrNull,
                    imageSize = CodeTheme.dimens.staticGrid.x6,
                    textStyle = CodeTheme.typography.displaySmall,
                    spacing = CodeTheme.dimens.grid.x2,
                )

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = state.descriptionFieldState.text.toString(),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            state.bill?.let { bill ->
                RenderedBill(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .sharedBoundsTransition(
                            transition = SharedTransition.CurrencyBill
                        ),
                    bill = bill,
                )
            }
        }
    }
}
