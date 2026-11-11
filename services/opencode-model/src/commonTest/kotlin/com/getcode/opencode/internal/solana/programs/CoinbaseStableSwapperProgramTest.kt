package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoinbaseStableSwapperProgramTest {

    private fun testKey(seed: Int): PublicKey =
        PublicKey(ByteArray(32) { seed.toByte() }.toList())

    // --- Program address ---

    @Test
    fun `program address is correct`() {
        assertEquals(
            "pqgqKahpG1y2wsgxFhzaAnkV1cL9vk8MSg9qm4q646F",
            CoinbaseStableSwapperProgram.address.base58()
        )
    }

    // --- SwapV2 ---

    private val goldenKeys = (0..11).map { i ->
        PublicKey(ByteArray(32) { if (it == 0) (i + 1).toByte() else 0 }.toList())
    }

    private fun goldenV2() = CoinbaseStableSwapperProgram_SwapV2(
        pool = goldenKeys[0], inVault = goldenKeys[1], outVault = goldenKeys[2],
        inVaultTokenAccount = goldenKeys[3], outVaultTokenAccount = goldenKeys[4],
        userFromTokenAccount = goldenKeys[5], toTokenAccount = goldenKeys[6],
        feeRecipientTokenAccount = goldenKeys[7], feeRecipient = goldenKeys[8],
        fromMint = goldenKeys[9], toMint = goldenKeys[10],
        user = goldenKeys[11],
        amountIn = 1_500_000L, minAmountOut = 1_490_000L,
    ).instruction()

    @Test
    fun swapV2GoldenData() {
        val expected = listOf(
            43, 4, 237, 11, 26, 201, 30, 98,
            0x60, 0xe3, 0x16, 0, 0, 0, 0, 0,
            0x50, 0xbc, 0x16, 0, 0, 0, 0, 0,
        ).map { it.toByte() }
        assertEquals(expected, goldenV2().data)
    }

    @Test
    fun swapV2AccountsHaveNoWhitelist() {
        val v2 = goldenV2()
        assertEquals(15, v2.accounts.size)
        assertEquals(goldenKeys.take(12), v2.accounts.take(12).map { it.publicKey })
        assertEquals(CoinbaseStableSwapperProgram.address, v2.program)
    }

    @Test
    fun swapV2TailAccountsAndFlags() {
        val a = goldenV2().accounts
        assertEquals(TokenProgram.address, a[12].publicKey)
        assertEquals(AssociatedTokenProgram.address, a[13].publicKey)
        assertEquals(SystemProgram.address, a[14].publicKey)
        assertEquals(setOf(11), a.indices.filter { a[it].isSigner }.toSet())
        assertEquals(setOf(3, 4, 5, 6, 7, 11), a.indices.filter { a[it].isWritable }.toSet())
    }
}
