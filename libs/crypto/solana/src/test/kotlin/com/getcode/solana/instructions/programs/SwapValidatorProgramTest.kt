package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwapValidatorProgramTest {

    private fun key(seed: Byte) = PublicKey(ByteArray(32) { seed }.toList())

    // --- PreSwap ---

    @Test
    fun preSwapRoundtrip() {
        val remaining = listOf(AccountMeta.readonly(publicKey = key(10)))
        val original = SwapValidatorProgram_PreSwap(
            preSwapState = key(1), user = key(2), source = key(3), destination = key(4),
            nonce = key(5), payer = key(6), remainingAccounts = remaining,
        )
        val decoded = SwapValidatorProgram_PreSwap.newInstance(original.instruction())
        assertEquals(original.preSwapState, decoded.preSwapState)
        assertEquals(original.user, decoded.user)
        assertEquals(original.source, decoded.source)
        assertEquals(original.destination, decoded.destination)
        assertEquals(original.nonce, decoded.nonce)
        assertEquals(original.payer, decoded.payer)
    }

    @Test
    fun preSwapNoRemainingAccounts() {
        val original = SwapValidatorProgram_PreSwap(
            preSwapState = key(1), user = key(2), source = key(3), destination = key(4),
            nonce = key(5), payer = key(6), remainingAccounts = emptyList(),
        )
        val decoded = SwapValidatorProgram_PreSwap.newInstance(original.instruction())
        assertEquals(original.preSwapState, decoded.preSwapState)
    }

    @Test
    fun preSwapEncodeLength() {
        val inst = SwapValidatorProgram_PreSwap(
            key(1), key(2), key(3), key(4), key(5), key(6), emptyList(),
        )
        // 8 bytes command only (no data fields)
        assertEquals(8, inst.encode().size)
    }

    // --- PostSwap ---

    @Test
    fun postSwapRoundtrip() {
        val original = SwapValidatorProgram_PostSwap(
            stateBump = 7.toByte(), maxToSend = 5_000_000L, minToReceive = 4_800_000L,
            preSwapState = key(1), source = key(2), destination = key(3), payer = key(4),
        )
        val decoded = SwapValidatorProgram_PostSwap.newInstance(original.instruction())
        assertEquals(7.toByte(), decoded.stateBump)
        assertEquals(5_000_000L, decoded.maxToSend)
        assertEquals(4_800_000L, decoded.minToReceive)
        assertEquals(original.preSwapState, decoded.preSwapState)
    }

    @Test
    fun postSwapZeroValues() {
        val original = SwapValidatorProgram_PostSwap(
            stateBump = 0, maxToSend = 0L, minToReceive = 0L,
            preSwapState = key(1), source = key(2), destination = key(3), payer = key(4),
        )
        val decoded = SwapValidatorProgram_PostSwap.newInstance(original.instruction())
        assertEquals(0.toByte(), decoded.stateBump)
        assertEquals(0L, decoded.maxToSend)
        assertEquals(0L, decoded.minToReceive)
    }

    @Test
    fun postSwapEncodeLength() {
        val inst = SwapValidatorProgram_PostSwap(
            stateBump = 0, maxToSend = 1L, minToReceive = 1L,
            preSwapState = key(1), source = key(2), destination = key(3), payer = key(4),
        )
        // 8 bytes command + 1 byte bump + 8 bytes maxToSend + 8 bytes minToReceive
        assertEquals(25, inst.encode().size)
    }

    // --- Command enum ---

    @Test
    fun commandValuesAreDistinct() {
        val commands = SwapValidatorProgram.Command.entries
        assertEquals(commands.size, commands.map { it.value }.toSet().size)
    }
}
