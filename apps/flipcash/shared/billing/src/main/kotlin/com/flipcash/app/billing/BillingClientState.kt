package com.flipcash.app.billing

data class BillingClientState(
    val connectionState: BillingClientConnection,
    val isPurchasePending: Boolean
) {
    fun canConnect() = connectionState.canConnect()
}