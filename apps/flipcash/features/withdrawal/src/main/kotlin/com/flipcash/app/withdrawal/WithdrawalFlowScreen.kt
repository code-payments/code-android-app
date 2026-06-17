package com.flipcash.app.withdrawal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenList
import com.flipcash.app.withdrawal.internal.screens.UsdcWithdrawalInformationScreen
import com.flipcash.app.withdrawal.internal.screens.WithdrawalConfirmationScreen
import com.flipcash.app.withdrawal.internal.screens.WithdrawalDestinationScreen
import com.flipcash.app.withdrawal.internal.screens.WithdrawalEntryScreen
import com.flipcash.core.R
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.LocalFlowNavigator
import com.getcode.navigation.flow.PreviewFlowNavigator
import com.getcode.navigation.flow.deliverFlowResult
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.flowAnnotatedEntry
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun WithdrawalFlowScreen(
    route: AppRoute.Transfers.Withdrawal,
    resultStateRegistry: NavResultStateRegistry,
) {
    val outerNavigator = LocalCodeNavigator.current

    val initialStack = route.rememberInitialStack<WithdrawalStep>()

    FlowHost(
        initialStack = initialStack,
        resultStateRegistry = resultStateRegistry,
        onExit = { reason, _ ->
            val result: WithdrawalResult = when (reason) {
                is FlowExitReason.Completed -> reason.result
                FlowExitReason.Canceled,
                FlowExitReason.BackedOutOfRoot -> WithdrawalResult.Canceled
            }
            outerNavigator.deliverFlowResult(
                route = route,
                value = NavResultOrCanceled.ReturnValue(result),
            )
            when (result) {
                WithdrawalResult.Success -> {
                    outerNavigator.popUntil { it == AppRoute.Sheets.Menu }
                }
                WithdrawalResult.Canceled -> {
                    outerNavigator.pop()
                }
            }
        },
        entryProvider = withdrawalEntryProvider(route.showOtherOptions),
    )
}

private fun withdrawalEntryProvider(
    showOtherOptions: Boolean,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<WithdrawalStep.UsdcInformational> {
        UsdcWithdrawalInformationScreen(showOtherOptions)
    }
    annotatedEntry<WithdrawalStep.SelectToken> {
        WithdrawalSelectTokenScreen()
    }
    flowAnnotatedEntry<WithdrawalStep.Amount> { step ->
        WithdrawalEntryScreen(step.mint)
    }
    annotatedEntry<WithdrawalStep.Destination> {
        WithdrawalDestinationScreen()
    }
    annotatedEntry<WithdrawalStep.Confirmation> {
        WithdrawalConfirmationScreen()
    }
}

@Composable
private fun WithdrawalSelectTokenScreen() {
    val flowNavigator = rememberFlowNavigator<WithdrawalStep, WithdrawalResult>()
    val viewModel = hiltViewModel<SelectTokenViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_selectCurrency),
            backButton = true,
            onBackIconClicked = { flowNavigator.back() },
            titleAlignment = Alignment.CenterHorizontally,
        )
        TokenList(
            modifier = Modifier.fillMaxSize(),
            tokens = state.tokens,
            selectedToken = state.selectedToken,
            onTokenSelected = { viewModel.dispatchEvent(SelectTokenViewModel.Event.OnTokenSelected(it.address)) },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(TokenPurpose.Withdraw))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
            .filter { it.fromUser }
            .map { it.mint }
            .onEach { mint ->
                flowNavigator.navigateTo(WithdrawalStep.Amount(mint))
            }.launchIn(this)
    }
}

@Composable
private fun WithdrawalFlowPreview(
    content: @Composable (state: WithdrawalViewModel.State) -> Unit
) {
    CompositionLocalProvider(
        LocalFlowNavigator provides PreviewFlowNavigator<WithdrawalStep, WithdrawalResult>(),
    ) {
        val state = WithdrawalViewModel.State()
        content(state)
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UsdcInformational() {
    WithdrawalFlowPreview { UsdcWithdrawalInformationScreen(showOtherOptions = true) }
}

