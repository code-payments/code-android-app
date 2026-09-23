package com.flipcash.analytics.events

import com.flipcash.analytics.Amount
import com.flipcash.analytics.CashLinkChoice
import com.flipcash.analytics.PropertiesBuilder
import com.flipcash.analytics.State
import com.flipcash.analytics.amount
import com.flipcash.analytics.event

object TransferEvents {
    fun grabBillStart() = event("Grab Bill Start")

    fun giveBillStart() = event("Give Bill Start")

    // DRIFT: iOS sends Grab Time in seconds, not milliseconds, and on failure sends only Fiat
    // and Currency of the amount (see Amount).
    fun grabBill(state: State, amount: Amount?, grabTimeMillis: Long?, error: String?) = event("Grab Bill") {
        number("Grab Time", grabTimeMillis)
        outcome(state, amount, error)
    }

    // DRIFT: iOS sends Exchange Rate and no USDC (see Amount).
    fun giveBill(state: State, amount: Amount?, error: String?) = event("Give Bill") {
        outcome(state, amount, error)
    }

    // DRIFT: iOS sends Exchange Rate and no USDC (see Amount).
    fun withdrawal(state: State, amount: Amount?, error: String?) = event("Withdrawal") {
        outcome(state, amount, error)
    }

    // DRIFT: iOS sends no Cash Link Choice or App, and sends Exchange Rate and no USDC (see
    // Amount). Android never sends Failure.
    fun sendCashLink(
        state: State,
        amount: Amount?,
        choice: CashLinkChoice?,
        app: String?,
        error: String?,
    ) = event("Send Cash Link") {
        text("Cash Link Choice", choice?.value)
        text("App", app)
        outcome(state, amount, error)
    }

    // DRIFT: iOS sends Exchange Rate and no USDC (see Amount).
    fun receiveCashLink(state: State, amount: Amount?, error: String?) = event("Receive Cash Link") {
        outcome(state, amount, error)
    }

    // DRIFT: iOS sends Exchange Rate and no USDC (see Amount).
    fun sentCash(state: State, amount: Amount?, error: String?) = event("Sent Cash") {
        outcome(state, amount, error)
    }

    // DRIFT: iOS sends Exchange Rate and no USDC (see Amount).
    fun sentTip(state: State, amount: Amount?, error: String?) = event("Sent Tip") {
        outcome(state, amount, error)
    }

    private fun PropertiesBuilder.outcome(state: State, amount: Amount?, error: String?) {
        text("State", state.value)
        amount(amount)
        text("Error", error)
    }
}
