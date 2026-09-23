package com.flipcash.analytics.events

import com.flipcash.analytics.Amount
import com.flipcash.analytics.PurchaseMethod
import com.flipcash.analytics.amount
import com.flipcash.analytics.event

/** Buying and selling a token. */
object SwapEvents {
    // DRIFT (Reserves): iOS also sends State, Payment Mint and Payment Token Symbol, and sends
    // only Fiat and Currency of the amount, with no USDC or Quarks.
    // Phantom and Coinbase are Android only; part 2 decides whether iOS sends them.
    fun purchase(method: PurchaseMethod, mint: String, amount: Amount, error: String?) =
        event("Token Purchase With ${method.value}") {
            text("Mint", mint)
            amount(amount)
            text("Error", error)
        }

    // DRIFT: iOS also sends State, and sends only Fiat and Currency of the amount, with no
    // USDC, Quarks or Fee.
    fun sell(mint: String, amount: Amount, fee: Double, error: String?) = event("Token Sell") {
        text("Mint", mint)
        amount(amount)
        number("Fee", fee)
        text("Error", error)
    }
}
