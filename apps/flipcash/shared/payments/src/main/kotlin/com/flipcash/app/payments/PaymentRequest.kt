package com.flipcash.app.payments

sealed interface PaymentRequest<T> {
    val rpcCall: (suspend () -> Result<T>)?
}