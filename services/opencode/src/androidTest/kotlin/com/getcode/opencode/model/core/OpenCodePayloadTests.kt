package com.getcode.opencode.model.core

import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.utils.nonce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(5.00, decoded.fiat?.decimalValue)
        assertEquals(nonce, decoded.nonce)
    }

    @Test
    fun tipPayloadEncoding() {
        val payload = OpenCodePayload(
            kind = PayloadKind.Tip,
            value = Username("bob"),
        )

        val encoded = payload.encode()
        val decoded = OpenCodePayload.Companion.fromList(encoded)

        // --------------------------------------------------------

        assertEquals(OpenCodePayload.LENGTH, encoded.size)
        assertEquals(PayloadKind.Tip.value, decoded.kind.value)
        // The username is hash-padded on encode and recovered by stripping at the '.' delimiter.
        assertEquals("bob", decoded.username)
        // Tip payloads carry no fiat amount and no nonce.
        assertNull(decoded.fiat)
        assertEquals(emptyList<Byte>(), decoded.nonce)
    }

    @Test
    fun tipPayloadEncodingFullLengthUsername() {
        val username = "fifteencharname" // exactly USERNAME_LENGTH (15)
        val payload = OpenCodePayload(
            kind = PayloadKind.Tip,
            value = Username(username),
        )

        val decoded = OpenCodePayload.Companion.fromList(payload.encode())

        assertEquals(username, decoded.username)
    }
}