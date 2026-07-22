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
        val userId: List<Byte> = (1..16).map { it.toByte() }
        val payload = OpenCodePayload(
            kind = PayloadKind.Tip,
            value = UserId(userId),
        )

        val encoded = payload.encode()
        val decoded = OpenCodePayload.Companion.fromList(encoded)

        // --------------------------------------------------------

        assertEquals(OpenCodePayload.LENGTH, encoded.size)
        assertEquals(PayloadKind.Tip.value, decoded.kind.value)
        // The 16-byte user id round-trips.
        assertEquals(userId, decoded.userId)
        // Tip payloads carry no fiat amount and no nonce.
        assertNull(decoded.fiat)
        assertEquals(emptyList<Byte>(), decoded.nonce)
    }

    @Test
    fun tipPayloadUserIdIsWrittenAtOffsetOne() {
        val userId: List<Byte> = (1..16).map { it.toByte() }
        val encoded = OpenCodePayload(
            kind = PayloadKind.Tip,
            value = UserId(userId),
        ).encode()

        assertEquals(PayloadKind.Tip.value, encoded[0].toInt())
        assertEquals(userId, encoded.subList(1, 17))
        // Trailing bytes reserved / zero.
        assertEquals(listOf<Byte>(0, 0, 0), encoded.subList(17, 20))
    }
}