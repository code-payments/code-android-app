package com.flipcash.analytics.events

import com.flipcash.analytics.AddMoneyMethod
import com.flipcash.analytics.AddMoneySource
import com.flipcash.analytics.Amount
import com.flipcash.analytics.State
import com.flipcash.analytics.amount
import com.flipcash.analytics.event

object AddMoneyEvents {
    fun opened(source: AddMoneySource) = event("Add Money: Opened") {
        text("Source", source.value)
    }

    // DRIFT: iOS has no Reserves method.
    fun methodSelected(method: AddMoneyMethod) = event("Add Money: Method Selected") {
        text("Method", method.value)
    }

    // DRIFT: iOS sends Exchange Rate and no USDC (see Amount).
    fun amountConfirmed(method: AddMoneyMethod, amount: Amount) = event("Add Money: Amount Confirmed") {
        text("Method", method.value)
        amount(amount)
    }

    // DRIFT: iOS sends Exchange Rate and no USDC (see Amount). Android's USDC carries the
    // native amount here.
    fun paymentInvoked(method: AddMoneyMethod, amount: Amount) = event("Add Money: Payment Invoked") {
        text("Method", method.value)
        amount(amount)
    }

    fun addressCopied(mint: String) = event("Add Money: Address Copied") {
        text("Mint", mint)
    }

    /** The outcome of an add-money flow, sent as the event named `Add Money`. */
    // DRIFT: iOS sends Exchange Rate and no USDC (see Amount).
    fun result(method: AddMoneyMethod, state: State, amount: Amount?, error: String?) = event("Add Money") {
        text("Method", method.value)
        text("State", state.value)
        amount(amount)
        text("Error", error)
    }
}
