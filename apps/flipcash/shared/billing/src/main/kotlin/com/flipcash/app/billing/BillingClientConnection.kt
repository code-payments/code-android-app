package com.flipcash.app.billing

import com.android.billingclient.api.Purchase

enum class BillingClientConnection {
    Disconnected,
    Connecting,
    Connected,
    ConnectionLost,
    Failed;

    fun canConnect() = this == Disconnected || this == ConnectionLost || this == Failed
}