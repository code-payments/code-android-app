package com.flipcash.app.deposit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.deposit.DepositResult
import com.flipcash.app.core.deposit.DepositStep
import com.flipcash.app.deposit.internal.DepositViewModel
import com.flipcash.app.deposit.internal.UsdcDepositInformationScreen
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.LocalFlowNavigator
import com.getcode.navigation.flow.PreviewFlowNavigator
import com.getcode.navigation.flow.deliverFlowResult
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.solana.keys.Mint

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
        onExit = { reason ->
            val result: DepositResult = when (reason) {
                is FlowExitReason.Completed -> reason.result
                FlowExitReason.Canceled,
                FlowExitReason.BackedOutOfRoot -> DepositResult.Canceled
            }
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
        },
        entryProvider = depositEntryProvider(route.mint),
    )
}

private fun depositEntryProvider(
    mint: Mint,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<DepositStep.UsdcInformational> {
        UsdcDepositInformationScreen()
    }
    annotatedEntry<DepositStep.Destination> {
        DepositDestinationScreen(mint)
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
    DepositFlowPreview { UsdcDepositInformationScreen() }
}
