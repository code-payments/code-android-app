package com.flipcash.app.tokens

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.toast.LocalToastController
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.getcode.opencode.model.financial.Fiat
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
    val toastController = LocalToastController.current
    val initialStack = route.rememberInitialStack<SwapStep>()

    FlowHost<SwapStep, SwapResult>(
        initialStack = initialStack,
        resultStateRegistry = resultStateRegistry,
        onExit = { reason, _ ->
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
                is SwapResult.Success -> {
                    if (route.shortfall != null || route.popToRoot) {
                        if (result.amount > Fiat.Zero) {
                            toastController.showToast(result.amount, isDeposit = true)
                        }
                        outerNavigator.popAll()
                    }
                    // pop() at root triggers onRootReached → sheet dismiss
                    outerNavigator.pop()
                }
                SwapResult.OpenDeposit,
                SwapResult.Canceled -> outerNavigator.pop()
            }
        },
        entryProvider = swapEntryProvider(route),
    )
}

private fun swapEntryProvider(
    route: AppRoute.Token.Swap,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    // When the flow begins directly at the connect screen (deposit-first "Add Money"
    // via Phantom), there is no preceding amount-entry step. Carry the purpose through
    // so that, once connected, we can route into amount entry instead of the
    // transaction-confirmation gate.
    val depositFirstPurpose = (route.purpose as? SwapPurpose.Buy)
        ?.takeIf { it.fundingSource == FundingSource.Phantom }

    flowAnnotatedEntry<SwapStep.Entry> { step ->
        SwapEntryScreen(step.purpose, step.initialAmount)
    }
    annotatedEntry<SwapStep.SellReceipt> { SellReceiptScreen() }
    annotatedEntry<SwapStep.PhantomConnect> {
        PhantomConnectConfirmationScreen(depositFirstPurpose = depositFirstPurpose)
    }
    annotatedEntry<SwapStep.PhantomConfirmTransaction> {
        PhantomTransactionConfirmationScreen()
    }
    annotatedEntry<SwapStep.Processing> {
        SwapProcessingScreen()
    }
}
