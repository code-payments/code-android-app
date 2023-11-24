package com.getcode.codeScanner

import com.getcode.model.CodePayload
import com.getcode.model.Kin
import com.getcode.model.Kind
import junit.framework.Assert.assertEquals
import org.junit.Test

class PayloadEncodingTest {
    @Test
    fun payloadEncoding() {
        val encodedData = listOf<Byte>(
            0x00, 0x40, 0x4B, 0x4C, 0x00, 0x0, 0x00, 0x00, 0x00, 0x01,
            0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x10, 0x11
        )
        val nonceData = listOf<Byte>(
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x10, 0x11
        )
        val payload = CodePayload(
            kind = Kind.Cash,
            kin = Kin.fromKin(50),
            nonce = nonceData
        )

        val encoded = payload.encode()
        val decoded = CodePayload.Companion.fromList(encoded)

        // --------------------------------------------------------

        assertEquals(Kind.Cash.value, decoded.kind.value)
        assertEquals(50, decoded.kin.toKin().toInt())
        assertEquals(nonceData, decoded.nonce)

        assertEquals(encodedData, encoded)
    }
}