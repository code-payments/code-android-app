package com.getcode.crypt

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha512Test {

    private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun `hashes the empty input`() {
        assertEquals(
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
                "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
            Sha512.hash(ByteArray(0)).hex(),
        )
    }

    @Test
    fun `hashes abc`() {
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            Sha512.hash("abc".encodeToByteArray()).hex(),
        )
    }

    @Test
    fun `hashes a range of the input`() {
        val padded = "xxabcxx".encodeToByteArray()
        assertEquals(
            Sha512.hash("abc".encodeToByteArray()).hex(),
            Sha512.hash(padded, 2, 3).hex(),
        )
    }

    @Test
    fun `digest length is 64 bytes`() {
        assertEquals(Sha512.LENGTH, Sha512.hash("abc".encodeToByteArray()).size)
    }
}
