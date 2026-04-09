package com.coinbase.onramp.data

import kotlin.time.Clock

sealed interface OnRampPurchaseRequest {
    val paymentCurrency: String // "USD"
    val paymentMethod: OnRampPaymentMethod
    val email: String // "satoshi.nakamoto@coinbase.com"
    val phoneNumber: String // "+14155551234"
    val partnerUserRef: String // "user123"
    val purchaseCurrency: String
    val destinationAddress: String
    val destinationNetwork: String // solana

    /**
     * When submitting this request, the returned quote will be inclusive of fees
     * i.e. the user will pay this exact amount of the payment currency.
     * @property paymentAmount The amount of fiat to spend, in the currency specified by `paymentCurrency`.
     * @property partnerUserRef A unique identifier for the user in your system.
     * @property paymentMethod The payment method to use for the purchase.
     * @property email The user's email address.
     * @property phoneNumber The user's phone number.
     * @property paymentCurrency The currency of the fiat to spend. Currently only “USD” is supported.
     * @property purchaseCurrency The cryptocurrency to purchase. Currently, only USDF is supported.
     */
    data class InclusiveOfFees(
        val paymentAmount: String, // "10"
        override val partnerUserRef: String,
        override val paymentMethod: OnRampPaymentMethod,
        override val email: String,
        override val phoneNumber: String,
        override val destinationAddress: String,
    ): OnRampPurchaseRequest {
        override val paymentCurrency: String = "USD"
        override val purchaseCurrency: String = "USDF"
        override val destinationNetwork: String = "solana"
    }

    /**
     * When submitting this request, the returned quote will be exclusive of fees
     * i.e. the user will receive this exact amount of the purchase currency.
     *
     * @property purchaseAmount The amount of cryptocurrency to purchase, in the currency specified by `purchaseCurrency`.
     * @property partnerUserRef A unique identifier for the user in your system.
     * @property paymentMethod The payment method to use for the purchase. Currently only [OnRampPaymentMethod.GUEST_CHECKOUT_APPLE_PAY] is supported.
     * @property phoneNumber The user's phone number.
     * @property paymentCurrency The currency of the fiat to spend. Currently only “USD” is supported.
     * @property purchaseCurrency The cryptocurrency to purchase. Currently, only USDF is supported.
     */
    data class ExclusiveOfFees(
        val purchaseAmount: String, // "10"
        override val partnerUserRef: String,
        override val paymentMethod: OnRampPaymentMethod,
        override val email: String,
        override val phoneNumber: String,
        override val destinationAddress: String,
    ): OnRampPurchaseRequest {
        override val paymentCurrency: String = "USD"
        override val purchaseCurrency: String = "USDF"
        override val destinationNetwork: String = "solana"
    }

    fun asMap(): Map<String, String> {
        return when (this) {
            is InclusiveOfFees -> mapOf(
                "paymentAmount" to paymentAmount,
                "partnerUserRef" to partnerUserRef,
                "paymentMethod" to paymentMethod.name,
                "email" to email,
                "phoneNumber" to phoneNumber,
                "paymentCurrency" to paymentCurrency,
                "purchaseCurrency" to purchaseCurrency,
                "destinationAddress" to destinationAddress,
                "destinationNetwork" to destinationNetwork,
                "agreementAcceptedAt" to Clock.System.now().toString(),
                "phoneNumberVerifiedAt" to Clock.System.now().toString(),
            )

            is ExclusiveOfFees -> mapOf(
                "purchaseAmount" to purchaseAmount,
                "partnerUserRef" to partnerUserRef,
                "paymentMethod" to paymentMethod.name,
                "email" to email,
                "phoneNumber" to phoneNumber,
                "paymentCurrency" to paymentCurrency,
                "purchaseCurrency" to purchaseCurrency,
                "destinationAddress" to destinationAddress,
                "destinationNetwork" to destinationNetwork,
                "agreementAcceptedAt" to Clock.System.now().toString(),
                "phoneNumberVerifiedAt" to Clock.System.now().toString(),
            )
        }
    }
}