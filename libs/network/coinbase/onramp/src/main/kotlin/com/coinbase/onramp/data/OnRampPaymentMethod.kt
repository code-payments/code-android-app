package com.coinbase.onramp.data

import kotlinx.serialization.Serializable

@Serializable
enum class OnRampPaymentMethod {
    GUEST_CHECKOUT_APPLE_PAY,
    GUEST_CHECKOUT_GOOGLE_PAY,
    ;
}