package com.flipcash.app.tokens

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flowAnnotatedEntry
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.deliverFlowResult
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry

@Composable
fun SwapFlowScreen(
    route: AppRoute.Token.Swap,
    resultStateRegistry: NavResultStateRegistry,
) {
    val outerNavigator = LocalCodeNavigator.current
    val initialStack = route.rememberInitialStack<SwapStep>()

    FlowHost<SwapStep, SwapResult>(
        initialStack = initialStack,
        resultStateRegistry = resultStateRegistry,
        onExit = { reason ->
            val result = when (reason) {
                is FlowExitReason.Completed -> reason.result
                FlowExitReason.Canceled,
                FlowExitReason.BackedOutOfRoot -> SwapResult.Canceled
            }
            outerNavigator.deliverFlowResult(
                route = route,
                value = NavResultOrCanceled.ReturnValue(result),
            )
            when (result) {
                SwapResult.Success -> {
                    if (route.shortfall != null) outerNavigator.popAll()
                    else outerNavigator.popUntil { it is AppRoute.Token.Info }
                }
                SwapResult.Canceled -> outerNavigator.pop()
            }
        },
        entryProvider = swapEntryProvider(),
    )
}

private fun swapEntryProvider(): (NavKey) -> NavEntry<NavKey> = entryProvider {
    flowAnnotatedEntry<SwapStep.Entry> { step ->
        SwapEntryScreen(step.purpose, step.initialAmount)
    }
    annotatedEntry<SwapStep.SellReceipt> { SellReceiptScreen() }
    annotatedEntry<SwapStep.PhantomConnect> {
        PhantomConnectConfirmationScreen()
    }
    annotatedEntry<SwapStep.PhantomConfirmTransaction> {
        PhantomTransactionConfirmationScreen()
    }
    annotatedEntry<SwapStep.Processing> {
        SwapProcessingScreen()
    }
}
