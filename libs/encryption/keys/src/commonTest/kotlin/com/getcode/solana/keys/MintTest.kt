package com.getcode.solana.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MintTest {

    @Test
    fun `Mint usdc has correct base58 address`() {
        assertEquals("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", Mint.usdc.base58())
    }

    @Test
    fun `Mint usdc is a PublicKey instance`() {
        assertTrue(Mint.usdc is PublicKey)
    }

    @Test
    fun `two calls to Mint usdc produce equal objects`() {
        val usdc1 = Mint.usdc
        val usdc2 = Mint.usdc

        assertEquals(usdc1, usdc2)
        assertEquals(usdc1.bytes, usdc2.bytes)
    }

    @Test
    fun `Mint usdf and usdc are different`() {
        val usdf = Mint.usdf
        val usdc = Mint.usdc

        assertTrue(usdf.bytes != usdc.bytes)
        assertTrue(usdf.base58() != usdc.base58())
    }

    @Test
    fun `Mint usdf has correct base58 address`() {
        assertEquals("5AMAA9JV9H97YYVxx8F6FsCMmTwXSuTTQneiup4RYAUQ", Mint.usdf.base58())
    }
}
