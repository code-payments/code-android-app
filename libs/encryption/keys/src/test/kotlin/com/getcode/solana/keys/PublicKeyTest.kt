package com.getcode.solana.keys

import com.getcode.vendor.Base58
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PublicKeyTest {

    private val tokenProgramAddress = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

    @Test
    fun `create from bytes and verify base58 roundtrip`() {
        val bytes = ByteArray(32) { it.toByte() }.toList()
        val key = PublicKey(bytes)

        val base58String = key.base58()
        val decoded = Base58.decode(base58String).toList()

        assertEquals(bytes, decoded)
    }

    @Test
    fun `create from base58 string and verify bytes match`() {
        val expectedBytes = Base58.decode(tokenProgramAddress).toList()
        val key = PublicKey(tokenProgramAddress)

        assertEquals(expectedBytes, key.bytes)
        assertEquals(tokenProgramAddress, key.base58())
    }

    @Test
    fun `fromBase58 factory method creates correct key`() {
        val key = PublicKey.fromBase58(tokenProgramAddress)

        assertEquals(tokenProgramAddress, key.base58())
        assertEquals(32, key.bytes.size)
    }

    @Test
    fun `equals returns true for same bytes`() {
        val bytes = ByteArray(32) { it.toByte() }.toList()
        val key1 = PublicKey(bytes)
        val key2 = PublicKey(bytes)

        assertEquals(key1, key2)
    }

    @Test
    fun `equals returns false for different bytes`() {
        val bytes1 = ByteArray(32) { it.toByte() }.toList()
        val bytes2 = ByteArray(32) { (it + 1).toByte() }.toList()
        val key1 = PublicKey(bytes1)
        val key2 = PublicKey(bytes2)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `equals returns true for keys created from same base58`() {
        val key1 = PublicKey(tokenProgramAddress)
        val key2 = PublicKey.fromBase58(tokenProgramAddress)

        assertEquals(key1, key2)
    }

    @Test
    fun `toString returns base58 encoding`() {
        val key = PublicKey(tokenProgramAddress)

        assertEquals(tokenProgramAddress, key.toString())
    }

    @Test
    fun `description returns base58 encoding`() {
        val key = PublicKey(tokenProgramAddress)

        assertEquals(tokenProgramAddress, key.description)
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val bytes = ByteArray(32) { it.toByte() }.toList()
        val key1 = PublicKey(bytes)
        val key2 = PublicKey(bytes)

        assertEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun `hashCode differs for different keys`() {
        val key1 = PublicKey(ByteArray(32) { it.toByte() }.toList())
        val key2 = PublicKey(ByteArray(32) { (it + 1).toByte() }.toList())

        assertNotEquals(key1.hashCode(), key2.hashCode())
    }

    @Test
    fun `key has 32 bytes`() {
        val key = PublicKey(tokenProgramAddress)

        assertEquals(32, key.bytes.size)
        assertEquals(32, key.size)
    }

    @Test
    fun `MAX_SEEDS is 16`() {
        assertEquals(16, PublicKey.MAX_SEEDS)
    }
}
