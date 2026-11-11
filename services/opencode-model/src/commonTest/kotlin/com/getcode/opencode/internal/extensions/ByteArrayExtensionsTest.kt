package com.getcode.opencode.internal.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteArrayExtensionsTest {

    @Test
    fun toPublicKeyPreservesBytes() {
        val bytes = ByteArray(32) { (it + 1).toByte() }
        val key = bytes.toPublicKey()
        assertEquals(bytes.toList(), key.bytes)
    }

    @Test
    fun toMintPreservesBytes() {
        val bytes = ByteArray(32) { it.toByte() }
        val mint = bytes.toMint()
        assertEquals(bytes.toList(), mint.bytes)
    }

    @Test
    fun toSignaturePreservesBytes() {
        val bytes = ByteArray(64) { it.toByte() }
        val signature = bytes.toSignature()
        assertEquals(bytes.toList(), signature.bytes)
    }

    @Test
    fun toHashPreservesBytes() {
        val bytes = ByteArray(32) { (it * 2).toByte() }
        val hash = bytes.toHash()
        assertEquals(bytes.toList(), hash.bytes)
    }

    @Test
    fun ubyteArrayToPublicKey() {
        val ubytes = UByteArray(32) { (it + 1).toUByte() }
        val key = ubytes.toPublicKey()
        assertEquals(32, key.bytes.size)
    }
}
