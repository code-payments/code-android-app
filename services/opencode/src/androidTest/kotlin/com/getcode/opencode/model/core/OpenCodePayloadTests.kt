package com.getcode.opencode.model.core

import com.getcode.opencode.utils.nonce
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCodePayloadTests {

    @Test
    fun payloadEncoding() {

        val nonce = nonce
        val payload = OpenCodePayload(
            kind = PayloadKind.Cash,
            value = 5.00.toFiat(),
            nonce = nonce
        )

        val encoded = payload.encode()
        val decoded = OpenCodePayload.Companion.fromList(encoded)

        // --------------------------------------------------------

        assertEquals(PayloadKind.Cash.value, decoded.kind.value)
        assertEquals(5.00, decoded.fiat?.doubleValue)
        assertEquals(nonce, decoded.nonce)
    }
}