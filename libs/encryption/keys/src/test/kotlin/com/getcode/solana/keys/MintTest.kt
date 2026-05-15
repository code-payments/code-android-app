package com.getcode.solana.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MintTest {

    @Test
    fun `Mint kin has correct base58 address`() {
        assertEquals("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6", Mint.kin.base58())
    }

    @Test
    fun `Mint usdc has correct base58 address`() {
        assertEquals("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", Mint.usdc.base58())
    }

    @Test
    fun `Mint kin is a PublicKey instance`() {
        assertTrue(Mint.kin is PublicKey)
    }

    @Test
    fun `Mint usdc is a PublicKey instance`() {
        assertTrue(Mint.usdc is PublicKey)
    }

    @Test
    fun `two calls to Mint kin produce equal objects`() {
        val kin1 = Mint.kin
        val kin2 = Mint.kin

        assertEquals(kin1, kin2)
        assertEquals(kin1.bytes, kin2.bytes)
    }

    @Test
    fun `two calls to Mint usdc produce equal objects`() {
        val usdc1 = Mint.usdc
        val usdc2 = Mint.usdc

        assertEquals(usdc1, usdc2)
        assertEquals(usdc1.bytes, usdc2.bytes)
    }

    @Test
    fun `Mint kin has 32 bytes`() {
        assertEquals(32, Mint.kin.bytes.size)
    }

    @Test
    fun `Mint kin and usdc are different`() {
        val kin = Mint.kin
        val usdc = Mint.usdc

        assertTrue(kin.bytes != usdc.bytes)
        assertTrue(kin.base58() != usdc.base58())
    }

    @Test
    fun `Mint usdf has correct base58 address`() {
        assertEquals("5AMAA9JV9H97YYVxx8F6FsCMmTwXSuTTQneiup4RYAUQ", Mint.usdf.base58())
    }
}
