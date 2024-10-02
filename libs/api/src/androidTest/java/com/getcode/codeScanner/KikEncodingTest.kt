package com.getcode.codeScanner

import com.getcode.network.repository.toByteList
import junit.framework.Assert.assertEquals
import org.junit.Test

class KikEncodingTest {
    @Test
    fun kikEncoding() {
        val encoded: List<Byte> = CodeScanner.Encode(
            listOf(
                0, 64, 75, 76, 0, 0, 0, 0, 0, 61, 86, 30, 96, 221, 64, 70, 137, 136, 106, 154
            )
                .toByteList()
                .toByteArray()
        )
            .toList()

        val expectedEncoded: List<Byte> =
            listOf(
                166, 113, 97, 198, 249, 29, 149, 39, 234, 219, 180, 240, 41, 2, 0, 0, 64, 75, 76,
                0, 0, 0, 0, 0, 61, 86, 30, 96, 221, 64, 70, 137, 136, 106, 154
            )
                .toByteList()

        assertEquals(expectedEncoded, encoded)
    }
}