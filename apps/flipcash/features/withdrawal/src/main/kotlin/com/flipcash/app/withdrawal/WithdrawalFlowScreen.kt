package com.flipcash.app.withdrawal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.app.withdrawal.internal.screens.UsdcWithdrawalInformationScreen
import com.flipcash.app.withdrawal.internal.screens.UsdcWithdrawalProcessingScreen
import com.flipcash.app.withdrawal.internal.screens.WithdrawalConfirmationScreen
import com.flipcash.app.withdrawal.internal.screens.WithdrawalDestinationScreen
import com.flipcash.app.withdrawal.internal.screens.WithdrawalEntryScreen
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.LocalFlowNavigator
import com.getcode.navigation.flow.PreviewFlowNavigator
import com.getcode.navigation.flow.deliverFlowResult
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.flowAnnotatedEntry
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.solana.keys.Mint

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
        onExit = { reason ->
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
        entryProvider = withdrawalEntryProvider(route.mint),
    )
}

private fun withdrawalEntryProvider(
    mint: Mint,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<WithdrawalStep.UsdcInformational> {
        UsdcWithdrawalInformationScreen()
    }
    flowAnnotatedEntry<WithdrawalStep.Amount> { step ->
        WithdrawalEntryScreen(step.mint)
    }
    annotatedEntry<WithdrawalStep.Destination> {
        WithdrawalDestinationScreen()
    }
    annotatedEntry<WithdrawalStep.Confirmation> {
        WithdrawalConfirmationScreen(mint = mint)
    }
    annotatedEntry<WithdrawalStep.Processing> {
        UsdcWithdrawalProcessingScreen()
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
    WithdrawalFlowPreview { UsdcWithdrawalInformationScreen() }
}

