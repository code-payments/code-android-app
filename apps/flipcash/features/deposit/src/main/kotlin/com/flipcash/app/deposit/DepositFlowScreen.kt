package com.flipcash.app.deposit

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
import com.flipcash.app.core.deposit.DepositResult
import com.flipcash.app.core.deposit.DepositStep
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.deposit.internal.DepositViewModel
import com.flipcash.app.deposit.internal.UsdcDepositInformationScreen
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenList
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
fun DepositFlowScreen(
    route: AppRoute.Transfers.Deposit,
    resultStateRegistry: NavResultStateRegistry,
) {
    val outerNavigator = LocalCodeNavigator.current
    val initialStack = route.rememberInitialStack<DepositStep>()

    FlowHost(
        initialStack = initialStack,
        resultStateRegistry = resultStateRegistry,
        onExit = { reason, isSheetRoot ->
            val result: DepositResult = when (reason) {
                is FlowExitReason.Completed -> reason.result
                FlowExitReason.Canceled,
                FlowExitReason.BackedOutOfRoot -> DepositResult.Canceled
            }
            if (isSheetRoot) {
                outerNavigator.pop()
            } else {
                outerNavigator.deliverFlowResult(
                    route = route,
                    value = NavResultOrCanceled.ReturnValue(result),
                )
                when (result) {
                    DepositResult.Success -> {
                        outerNavigator.popUntil { it == AppRoute.Sheets.Menu }
                    }
                    DepositResult.Canceled -> {
                        outerNavigator.pop()
                    }
                }
            }
        },
        entryProvider = depositEntryProvider(route.showOtherOptions),
    )
}

private fun depositEntryProvider(
    showOtherOptions: Boolean,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    flowAnnotatedEntry<DepositStep.UsdcInformational> {
        UsdcDepositInformationScreen(showOtherOptions)
    }
    annotatedEntry<DepositStep.SelectToken> {
        DepositSelectTokenScreen()
    }
    annotatedEntry<DepositStep.Destination> { key ->
        DepositDestinationScreen(key.mint)
    }
}

@Composable
private fun DepositSelectTokenScreen() {
    val flowNavigator = rememberFlowNavigator<DepositStep, DepositResult>()
    val viewModel = hiltViewModel<SelectTokenViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            isInModal = true,
            title = stringResource(R.string.title_selectCurrency),
            backButton = true,
            onBackIconClicked = { flowNavigator.back() },
            titleAlignment = Alignment.CenterHorizontally,
        )
        TokenList(
            modifier = Modifier.fillMaxSize(),
            tokens = state.tokens,
            selectedToken = state.selectedToken,
            showFlags = true,
            includeReserves = true,
            onTokenSelected = { viewModel.dispatchEvent(SelectTokenViewModel.Event.OnTokenSelected(it.address)) },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(TokenPurpose.Deposit))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
            .filter { it.fromUser }
            .map { it.mint }
            .onEach { mint ->
                flowNavigator.navigateTo(DepositStep.Destination(mint))
            }.launchIn(this)
    }
}

@Composable
private fun DepositFlowPreview(
    content: @Composable (state: DepositViewModel.State) -> Unit
) {
    CompositionLocalProvider(
        LocalFlowNavigator provides PreviewFlowNavigator<DepositStep, DepositResult>(),
    ) {
        val state = DepositViewModel.State()
        content(state)
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UsdcInformational() {
    DepositFlowPreview { UsdcDepositInformationScreen(true) }
}
