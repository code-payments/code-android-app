package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.Mint
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestShareTest {

    @Test
    fun `a cash message carrying a request id pays a share`() {
        assertTrue(cash(paymentRequestId = byteArrayOf(1, 2, 3).toList()).paysRequestShare)
    }

    @Test
    fun `a cash message without a request id is a normal payment`() {
        assertFalse(cash(paymentRequestId = null).paysRequestShare)
    }

    @Test
    fun `other content never pays a share`() {
        assertFalse(MessageContent.Text("hi").paysRequestShare)
    }

    private fun cash(paymentRequestId: List<Byte>?) =
        MessageContent.Cash(
            intentId = listOf(1),
            amount = 5.toFiat(),
            mint = Mint.usdf,
            paymentRequestId = paymentRequestId,
        )
}
