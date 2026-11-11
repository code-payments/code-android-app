package com.getcode.opencode.solana.swap

import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.internal.solana.programs.CoinbaseStableSwapperProgram
import com.getcode.opencode.model.transactions.FundSwapPool
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals

/** The client-built deposit swaps must use CoinbaseStableSwapper SwapV2: 15 accounts, v2 discriminator. */
class ClientBuiltDepositSwapV2Test {

    private val sender = PublicKey(ByteArray(32) { 3 }.toList())
    private val owner = PublicKey(ByteArray(32) { 7 }.toList())
    private val feeRecipient = PublicKey(ByteArray(32) { 5 }.toList())
    private val v2Discriminator = listOf<Byte>(43, 4, -19, 11, 26, -55, 30, 98)

    @Test
    fun `usdf deposit swap instruction is SwapV2`() {
        val ix = buildUsdfDepositInstructions(
            sender = sender,
            owner = owner,
            amount = 1_000_000L,
            feeRecipient = feeRecipient,
        ).single { it.program == CoinbaseStableSwapperProgram.address }

        assertEquals(15, ix.accounts.size)
        assertEquals(v2Discriminator, ix.data.take(8))
    }

    @Test
    fun `usdc to usdf coinbase swap instruction is SwapV2`() {
        val ix = buildUsdcToUsdfSwapInstructions(
            sender = sender,
            owner = owner,
            amount = 1_000_000L,
            pool = FundSwapPool.CoinbaseStableSwapper(feeRecipient = feeRecipient),
            swapId = SwapId(PublicKey(ByteArray(32) { 9 }.toList())),
        ).single { it.program == CoinbaseStableSwapperProgram.address }

        assertEquals(15, ix.accounts.size)
        assertEquals(v2Discriminator, ix.data.take(8))
    }
}
