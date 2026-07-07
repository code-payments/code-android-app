package com.coinbase.onramp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnRampPurchaseRequestTest {

    private fun inclusiveOf() = OnRampPurchaseRequest.InclusiveOfFees(
        paymentAmount = "25.00",
        partnerUserRef = "user-123",
        paymentMethod = OnRampPaymentMethod.GUEST_CHECKOUT_GOOGLE_PAY,
        email = "test@example.com",
        phoneNumber = "+12125551234",
        destinationAddress = "some-sol-address",
        purchaseCurrency = "USDF",
    )

    private fun exclusiveOf() = OnRampPurchaseRequest.ExclusiveOfFees(
        purchaseAmount = "10.00",
        partnerUserRef = "user-456",
        paymentMethod = OnRampPaymentMethod.GUEST_CHECKOUT_GOOGLE_PAY,
        email = "apple@example.com",
        phoneNumber = "+12125559876",
        destinationAddress = "another-sol-address",
        purchaseCurrency = "USDF",
    )

    // --- InclusiveOfFees ---

    @Test
    fun inclusiveContainsPaymentAmount() {
        assertEquals("25.00", inclusiveOf().asMap()["paymentAmount"])
    }

    @Test
    fun inclusiveDoesNotContainPurchaseAmount() {
        assertFalse(inclusiveOf().asMap().containsKey("purchaseAmount"))
    }

    @Test
    fun inclusiveHardcodesPaymentCurrency() {
        assertEquals("USD", inclusiveOf().asMap()["paymentCurrency"])
    }

    @Test
    fun inclusiveHardcodesPurchaseCurrency() {
        assertEquals("USDF", inclusiveOf().asMap()["purchaseCurrency"])
    }

    @Test
    fun inclusiveHardcodesDestinationNetwork() {
        assertEquals("solana", inclusiveOf().asMap()["destinationNetwork"])
    }

    @Test
    fun inclusiveContainsTimestampFields() {
        val map = inclusiveOf().asMap()
        assertTrue(map.containsKey("agreementAcceptedAt"))
        assertTrue(map.containsKey("phoneNumberVerifiedAt"))
        assertTrue(map["agreementAcceptedAt"]!!.isNotBlank())
        assertTrue(map["phoneNumberVerifiedAt"]!!.isNotBlank())
    }

    // --- ExclusiveOfFees ---

    @Test
    fun exclusiveContainsPurchaseAmount() {
        assertEquals("10.00", exclusiveOf().asMap()["purchaseAmount"])
    }

    @Test
    fun exclusiveDoesNotContainPaymentAmount() {
        assertFalse(exclusiveOf().asMap().containsKey("paymentAmount"))
    }

    @Test
    fun exclusiveContainsAllRequiredKeys() {
        val map = exclusiveOf().asMap()
        listOf(
            "purchaseAmount", "partnerUserRef", "paymentMethod", "email",
            "phoneNumber", "paymentCurrency", "purchaseCurrency",
            "destinationAddress", "destinationNetwork",
            "agreementAcceptedAt", "phoneNumberVerifiedAt",
        ).forEach { key ->
            assertTrue(map.containsKey(key), "Missing key: $key")
        }
    }

    @Test
    fun paymentMethodSerialized() {
        val map = inclusiveOf().asMap()
        assertEquals("GUEST_CHECKOUT_GOOGLE_PAY", map["paymentMethod"])
    }

    @Test
    fun destinationAddressPassedThrough() {
        assertEquals("some-sol-address", inclusiveOf().asMap()["destinationAddress"])
        assertEquals("another-sol-address", exclusiveOf().asMap()["destinationAddress"])
    }
}
