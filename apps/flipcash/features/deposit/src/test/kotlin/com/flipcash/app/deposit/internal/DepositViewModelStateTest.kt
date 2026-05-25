package com.flipcash.app.deposit.internal

import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepositViewModelStateTest {

    private val reduce = DepositViewModel.Companion.updateStateForEvent
    private fun mint() = com.getcode.solana.keys.Mint(ByteArray(32) { 1 }.toList())

    @Test
    fun `default state has null address and token name`() {
        val state = DepositViewModel.State()
        assertNull(state.selectedTokenAddress)
        assertNull(state.tokenName)
        assertEquals("", state.depositAddress)
        assertFalse(state.isCopied)
    }

    @Test
    fun `OnMintSelected sets selected token address`() {
        val mint = mint()
        val updated = reduce(DepositViewModel.Event.OnMintSelected(mint))(DepositViewModel.State())
        assertEquals(mint, updated.selectedTokenAddress)
    }

    @Test
    fun `OnTokenChanged sets address and name`() {
        val updated = reduce(
            DepositViewModel.Event.OnTokenChanged(address = "abc123", name = "TestToken")
        )(DepositViewModel.State())
        assertEquals("abc123", updated.depositAddress)
        assertEquals("TestToken", updated.tokenName)
    }

    @Test
    fun `SetCopied true then false roundtrip`() {
        val state = DepositViewModel.State()
        val copied = reduce(DepositViewModel.Event.SetCopied(true))(state)
        assertTrue(copied.isCopied)
        val uncopied = reduce(DepositViewModel.Event.SetCopied(false))(copied)
        assertFalse(uncopied.isCopied)
    }

    @Test
    fun `CopyAddress is no-op`() {
        val state = DepositViewModel.State(depositAddress = "addr", tokenName = "T")
        val updated = reduce(DepositViewModel.Event.CopyAddress)(state)
        assertEquals(state, updated)
    }

    @Test
    fun `Exit is no-op`() {
        val state = DepositViewModel.State(depositAddress = "addr")
        val updated = reduce(DepositViewModel.Event.Exit)(state)
        assertEquals(state, updated)
    }
}
