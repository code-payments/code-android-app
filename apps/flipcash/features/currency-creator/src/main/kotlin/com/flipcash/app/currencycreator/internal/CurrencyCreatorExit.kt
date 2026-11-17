package com.flipcash.app.currencycreator.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.getcode.navigation.Sheet
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.flow.deliverFlowResult
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.opencode.model.financial.Fiat

/**
 * Leaves the currency creator once its flow has exited, on the navigator that opened it: hands
 * [result] back to the caller, then closes the creator. A funded launch presents [launchBill] so
 * someone can grab the new currency.
 */
internal fun CodeNavigator.exitCurrencyCreator(
    route: AppRoute.Token.CurrencyCreator,
    result: CurrencyCreatorResult,
    shell: NavBarShell,
    launchBill: Scannable.Payable?,
    purchaseAmount: Fiat,
    requestNotifications: () -> Unit,
    showBill: (Scannable.Payable) -> Unit,
) {
    // With no sheet on the stack, hide() pops nothing, so a funded launch leaves the creator on
    // the stack under its bill. The Group Money shell pops the creator instead. Classic still
    // dead-ends there, as it has on code/cash since a12cf891b moved the wallet from a sheet to a
    // tab; dropping the shell check fixes it.
    val popsCreator = shell == NavBarShell.GroupMoney && backStack.none { it is Sheet }

    // Once popped the creator is gone, and a second exit (the success button tapped again while
    // it animates out) would pop the screen under it and show the bill again.
    if (popsCreator && backStack.lastOrNull() != route) return

    deliverFlowResult(
        route = route,
        value = NavResultOrCanceled.ReturnValue(result),
    )

    if (result is CurrencyCreatorResult.Success) {
        if (result.completedInPlace) requestNotifications()
        // The bill is priced through the new token's bonding curve in the
        // ViewModel (see buildLaunchBill); presenting it lets someone grab the
        // newly created currency. It's null if pricing failed — just close then.
        if (purchaseAmount > Fiat.Zero && launchBill != null) {
            if (popsCreator) pop() else hide()
            showBill(launchBill)
        } else {
            pop()
        }
    } else {
        pop()
    }
}
