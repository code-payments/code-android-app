package com.flipcash.services.billing

enum class BillingClientState {
    Disconnected,
    Connecting,
    Connected,
    ConnectionLost,
    Failed;

    fun canConnect() = this == Disconnected || this == ConnectionLost || this == Failed
}