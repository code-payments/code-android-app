package com.getcode.models

import com.getcode.services.model.CodePayload
import com.getcode.services.model.Kind
import com.getcode.services.model.Username
import junit.framework.Assert
import org.junit.Test

class CodePayloadTests {

    private val sampleTip = listOf<Byte>(
        5, 0, 0, 0, 0, 103, 101, 116, 99, 111, 100, 101, 46, 52, 86, 113, 47, 114, 43, 88
    )

    private val sampleTipDot = listOf<Byte>(
        5, 0, 0, 0, 0, 97, 110, 100, 114, 111, 105, 100, 100, 101, 118, 49, 50, 51, 52, 46
    )

    @Test
    fun testEncodeUsername() {
        val payload = CodePayload(
            kind = Kind.Tip,
            value = Username("getcode"),
        )

        val encoded = payload.encode()
        Assert.assertEquals(sampleTip, encoded)

    }

    @Test
    fun testEncodedSinglePad() {
        val payload = CodePayload(
            kind = Kind.Tip,
            value = Username("androiddev1234"),
        )

        val encoded = payload.encode()
        Assert.assertEquals(sampleTipDot, encoded)
    }

    @Test
    fun testEncodeUsernameTooLong() {
        val payload0 = CodePayload(
            kind = Kind.Tip,
            value = Username("androiddev1234_"), // max length
        )

        val payload1 = CodePayload(
            kind = Kind.Tip,
            value = Username("androiddev1234__"),
        )

        val payload2 = CodePayload(
            kind = Kind.Tip,
            value = Username("androiddev1234__1412412412"),
        )

        val expected = payload0.encode()

        Assert.assertEquals(expected, payload1.encode())
        Assert.assertEquals(expected, payload2.encode())
    }

    @Test
    fun testDecodeUsername() {
        val payload = CodePayload.fromList(sampleTip)
        Assert.assertEquals(payload.kind, Kind.Tip)
        Assert.assertEquals(payload.value, Username("getcode"))
        Assert.assertEquals(payload.nonce, emptyList<Byte>())
    }
}