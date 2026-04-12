package com.flipcash.app.withdrawal

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.getcode.navigation.flow.rememberInitialStack
import com.flipcash.app.withdrawal.internal.screens.WithdrawalConfirmationContent
import com.flipcash.app.withdrawal.internal.screens.WithdrawalDestinationContent
import com.flipcash.app.withdrawal.internal.screens.WithdrawalEntryContent
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.deliverFlowResult
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
        entryProvider = withdrawalEntryProvider(route.mint, outerNavigator),
    )
}

private fun withdrawalEntryProvider(
    mint: Mint,
    outerNavigator: CodeNavigator,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<WithdrawalStep.Amount> { step ->
        WithdrawalEntryContent(step.mint, outerNavigator)
    }
    annotatedEntry<WithdrawalStep.Destination> {
        WithdrawalDestinationContent()
    }
    annotatedEntry<WithdrawalStep.Confirmation> {
        WithdrawalConfirmationContent(mint = mint)
    }
}
