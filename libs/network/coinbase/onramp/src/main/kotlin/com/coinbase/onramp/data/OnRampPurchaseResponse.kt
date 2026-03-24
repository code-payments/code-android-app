package com.coinbase.onramp.data

import com.getcode.serialization.InstantIso8601Serializer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable


@Serializable
data class OnRampPurchaseResponse(
    val order: Order,
    val paymentLink: PaymentLink,
) {
    /**
     * @property orderId A unique identifier for the order.
     * @property paymentTotal The total amount the user will pay, including any fees.
     * @property paymentSubtotal The total amount the user will pay minus any applicable fees, in the payment
     * currency.
     * @property paymentCurrency The currency of the payment.
     * @property paymentMethod The payment method used for the purchase.
     * @property purchaseAmount The amount of crypto currency the user will receive after any applicable fees.
     * @property purchaseCurrency The crypto currency the user will receive.
     * @property fee The fee associated with the order.
     * @property exchangeRate The exchange rate between payment currency and purchase currency.
     * @property destinationAddress The address the crypto currency will be sent to.
     * @property paymentNetwork The network the crypto currency will be sent on.
     * @property status The current status of the order. When an order is first created, the status will be
     * “ONRAMP_ORDER_STATUS_PENDING_PAYMENT”. After the user completes the
     * payment, you can use the `/v1/buy/user/{partner_user_id}/transactions`
     * API to monitor the status of the order.
     * @see <a href="https://docs.cdp.coinbase.com/onramp/docs/api-reporting">API Reporting</a>.
     *
     * @property txHash When the order is complete and the send has been confirmed, this will contain the
     * transaction hash (signature on Solana) for the send.
     * @property createdAt The time this order was created.
     * @property updatedAt The time this order was updated.
     */
    @Serializable
    data class Order(
        val orderId: String,
        val paymentTotal: String,
        val paymentSubtotal: String,
        val paymentCurrency: String,
        val paymentMethod: OnRampPaymentMethod,
        val purchaseAmount: String,
        val purchaseCurrency: String,
        val fees: List<OnRampFee>,
        val exchangeRate: String,
        val destinationAddress: String,
        val status: String,
        val txHash: String? = null,
        val partnerUserRef: String,
        @Serializable(with = InstantIso8601Serializer::class)
        val createdAt: Instant,
        @Serializable(with = InstantIso8601Serializer::class)
        val updatedAt: Instant,
    )

    /**
     * @property type What the fee is for e.g. [OnRampFeeType.FEE_TYPE_EXCHANGE] for fees charged for
     * buying crypto, and [OnRampFeeType.FEE_TYPE_NETWORK] for fees charged for
     * sending the crypto to the destination address.
     * @property amount The amount of the fee.
     * @property currency The fiat currency of the fee.
     */
    @Serializable
    data class OnRampFee(
        val type: OnRampFeeType,
        val amount: String,
        val currency: String,
    )

    /**
     * @property url The URL containing the UI component the user must interact with to complete the
     * onramp Transaction. When using the payment method [OnRampPaymentMethod.GUEST_CHECKOUT_GOOGLE_PAY] this
     * URL will render a Google Pay button that the user must press.
     * @property paymentLinkType An enum indicating the type of payment link URL.
     */
    @Serializable
    data class PaymentLink(
        val url: String,
        val paymentLinkType: OnRampPaymentLinkType
    )
}
