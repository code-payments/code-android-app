package com.flipcash.analytics.events

import com.flipcash.analytics.Amount
import com.flipcash.analytics.WalletProvider
import com.flipcash.analytics.amount
import com.flipcash.analytics.event

/** The Phantom deeplink flow. */
object WalletEvents {
    // DRIFT: iOS sends no Provider, and fires on the request rather than after connecting.
    fun connect(provider: WalletProvider) = event("Wallet: Connect") {
        text("Provider", provider.value)
    }

    // DRIFT: iOS sends no Provider, and sends only Fiat (the native amount) and Currency where
    // Android sends the token amount's block (see Amount).
    fun requestAmount(provider: WalletProvider, amount: Amount) = event("Wallet: Request Amount") {
        text("Provider", provider.value)
        amount(amount)
    }

    // DRIFT: iOS declares this with no Provider and never sends it.
    fun transactionsFailed(provider: WalletProvider) = event("Wallet: Transactions Failed") {
        text("Provider", provider.value)
    }

    // DRIFT: iOS sends no Provider, and fires on any error code where Android fires only on a
    // user reject.
    fun cancel(provider: WalletProvider) = event("Wallet: Cancel") {
        text("Provider", provider.value)
    }
}
