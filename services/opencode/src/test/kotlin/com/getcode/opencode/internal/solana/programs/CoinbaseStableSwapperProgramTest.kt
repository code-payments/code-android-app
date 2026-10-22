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

    // --- Swap instruction discriminator ---

    @Test
    fun swapDiscriminatorBytes() {
        val ix = CoinbaseStableSwapperProgram_Swap(
            pool = testKey(1), inVault = testKey(2), outVault = testKey(3),
            inVaultTokenAccount = testKey(4), outVaultTokenAccount = testKey(5),
            userFromTokenAccount = testKey(6), toTokenAccount = testKey(7),
            feeRecipientTokenAccount = testKey(8), feeRecipient = testKey(9),
            fromMint = testKey(10), toMint = testKey(11),
            user = testKey(12), whitelist = testKey(13),
            amountIn = 0L, minAmountOut = 0L,
        )
        val encoded = ix.encode()
        // Anchor discriminator: [248, 198, 158, 145, 225, 117, 135, 200]
        assertEquals(248.toByte(), encoded[0])
        assertEquals(198.toByte(), encoded[1])
        assertEquals(158.toByte(), encoded[2])
        assertEquals(145.toByte(), encoded[3])
        assertEquals(225.toByte(), encoded[4])
        assertEquals(117.toByte(), encoded[5])
        assertEquals(135.toByte(), encoded[6])
        assertEquals(200.toByte(), encoded[7])
    }

    // --- Swap instruction encoding ---

    @Test
    fun swapEncodeLength() {
        val ix = CoinbaseStableSwapperProgram_Swap(
            pool = testKey(1), inVault = testKey(2), outVault = testKey(3),
            inVaultTokenAccount = testKey(4), outVaultTokenAccount = testKey(5),
            userFromTokenAccount = testKey(6), toTokenAccount = testKey(7),
            feeRecipientTokenAccount = testKey(8), feeRecipient = testKey(9),
            fromMint = testKey(10), toMint = testKey(11),
            user = testKey(12), whitelist = testKey(13),
            amountIn = 1000L, minAmountOut = 900L,
        )
        // 8 bytes discriminator + 8 bytes amountIn + 8 bytes minAmountOut = 24
        assertEquals(24, ix.encode().size)
    }

    @Test
    fun swapEncodesAmountsAsLittleEndian() {
        val ix = CoinbaseStableSwapperProgram_Swap(
            pool = testKey(1), inVault = testKey(2), outVault = testKey(3),
            inVaultTokenAccount = testKey(4), outVaultTokenAccount = testKey(5),
            userFromTokenAccount = testKey(6), toTokenAccount = testKey(7),
            feeRecipientTokenAccount = testKey(8), feeRecipient = testKey(9),
            fromMint = testKey(10), toMint = testKey(11),
            user = testKey(12), whitelist = testKey(13),
            amountIn = 1_000_000L, minAmountOut = 999_000L,
        )
        val encoded = ix.encode()

        // amountIn = 1_000_000 = 0x000F4240 LE → [0x40, 0x42, 0x0F, 0x00, ...]
        assertEquals(0x40.toByte(), encoded[8])
        assertEquals(0x42.toByte(), encoded[9])
        assertEquals(0x0F.toByte(), encoded[10])
        for (i in 11..15) assertEquals(0x00.toByte(), encoded[i])

        // minAmountOut = 999_000 = 0x000F3E58 LE → [0x58, 0x3E, 0x0F, 0x00, ...]
        assertEquals(0x58.toByte(), encoded[16])
        assertEquals(0x3E.toByte(), encoded[17])
        assertEquals(0x0F.toByte(), encoded[18])
        for (i in 19..23) assertEquals(0x00.toByte(), encoded[i])
    }

    // --- Swap instruction accounts ---

    @Test
    fun swapInstructionHas16Accounts() {
        val ix = CoinbaseStableSwapperProgram_Swap(
            pool = testKey(1), inVault = testKey(2), outVault = testKey(3),
            inVaultTokenAccount = testKey(4), outVaultTokenAccount = testKey(5),
            userFromTokenAccount = testKey(6), toTokenAccount = testKey(7),
            feeRecipientTokenAccount = testKey(8), feeRecipient = testKey(9),
            fromMint = testKey(10), toMint = testKey(11),
            user = testKey(12), whitelist = testKey(13),
            amountIn = 1000L, minAmountOut = 900L,
        )
        assertEquals(16, ix.instruction().accounts.size)
    }

    @Test
    fun swapInstructionProgram() {
        val ix = CoinbaseStableSwapperProgram_Swap(
            pool = testKey(1), inVault = testKey(2), outVault = testKey(3),
            inVaultTokenAccount = testKey(4), outVaultTokenAccount = testKey(5),
            userFromTokenAccount = testKey(6), toTokenAccount = testKey(7),
            feeRecipientTokenAccount = testKey(8), feeRecipient = testKey(9),
            fromMint = testKey(10), toMint = testKey(11),
            user = testKey(12), whitelist = testKey(13),
            amountIn = 1000L, minAmountOut = 900L,
        )
        assertEquals(CoinbaseStableSwapperProgram.address, ix.instruction().program)
    }

    @Test
    fun swapAccountOrder() {
        val keys = (1..13).map { testKey(it) }
        val ix = CoinbaseStableSwapperProgram_Swap(
            pool = keys[0], inVault = keys[1], outVault = keys[2],
            inVaultTokenAccount = keys[3], outVaultTokenAccount = keys[4],
            userFromTokenAccount = keys[5], toTokenAccount = keys[6],
            feeRecipientTokenAccount = keys[7], feeRecipient = keys[8],
            fromMint = keys[9], toMint = keys[10],
            user = keys[11], whitelist = keys[12],
            amountIn = 1000L, minAmountOut = 900L,
        )
        val accounts = ix.instruction().accounts

        // Verify account order matches server: pool, inVault, outVault, inVaultTA, outVaultTA,
        // userFromTA, toTA, feeRecipientTA, feeRecipient, fromMint, toMint, user, whitelist,
        // tokenProgram, associatedTokenProgram, systemProgram
        assertEquals(keys[0], accounts[0].publicKey)   // pool
        assertEquals(keys[1], accounts[1].publicKey)   // inVault
        assertEquals(keys[2], accounts[2].publicKey)   // outVault
        assertEquals(keys[3], accounts[3].publicKey)   // inVaultTokenAccount
        assertEquals(keys[4], accounts[4].publicKey)   // outVaultTokenAccount
        assertEquals(keys[5], accounts[5].publicKey)   // userFromTokenAccount
        assertEquals(keys[6], accounts[6].publicKey)   // toTokenAccount
        assertEquals(keys[7], accounts[7].publicKey)   // feeRecipientTokenAccount
        assertEquals(keys[8], accounts[8].publicKey)   // feeRecipient
        assertEquals(keys[9], accounts[9].publicKey)   // fromMint
        assertEquals(keys[10], accounts[10].publicKey)  // toMint
        assertEquals(keys[11], accounts[11].publicKey)  // user
        assertEquals(keys[12], accounts[12].publicKey)  // whitelist
        assertEquals(TokenProgram.address, accounts[13].publicKey)
        assertEquals(AssociatedTokenProgram.address, accounts[14].publicKey)
        assertEquals(SystemProgram.address, accounts[15].publicKey)
    }

    @Test
    fun swapAccountSignerFlags() {
        val ix = CoinbaseStableSwapperProgram_Swap(
            pool = testKey(1), inVault = testKey(2), outVault = testKey(3),
            inVaultTokenAccount = testKey(4), outVaultTokenAccount = testKey(5),
            userFromTokenAccount = testKey(6), toTokenAccount = testKey(7),
            feeRecipientTokenAccount = testKey(8), feeRecipient = testKey(9),
            fromMint = testKey(10), toMint = testKey(11),
            user = testKey(12), whitelist = testKey(13),
            amountIn = 1000L, minAmountOut = 900L,
        )
        val accounts = ix.instruction().accounts

        // Only user (index 11) is a signer
        for (i in accounts.indices) {
            if (i == 11) {
                assertTrue(accounts[i].isSigner, "user (index $i) should be signer")
            } else {
                assertFalse(accounts[i].isSigner, "account at index $i should not be signer")
            }
        }
    }

    @Test
    fun swapAccountWritableFlags() {
        val ix = CoinbaseStableSwapperProgram_Swap(
            pool = testKey(1), inVault = testKey(2), outVault = testKey(3),
            inVaultTokenAccount = testKey(4), outVaultTokenAccount = testKey(5),
            userFromTokenAccount = testKey(6), toTokenAccount = testKey(7),
            feeRecipientTokenAccount = testKey(8), feeRecipient = testKey(9),
            fromMint = testKey(10), toMint = testKey(11),
            user = testKey(12), whitelist = testKey(13),
            amountIn = 1000L, minAmountOut = 900L,
        )
        val accounts = ix.instruction().accounts

        // Writable: inVaultTokenAccount(3), outVaultTokenAccount(4), userFromTokenAccount(5),
        //           toTokenAccount(6), feeRecipientTokenAccount(7), user(11)
        val writableIndices = setOf(3, 4, 5, 6, 7, 11)
        for (i in accounts.indices) {
            if (i in writableIndices) {
                assertTrue(accounts[i].isWritable, "account at index $i should be writable")
            } else {
                assertFalse(accounts[i].isWritable, "account at index $i should not be writable")
            }
        }
    }

    // --- SwapV2 ---

    private val goldenKeys = (0..12).map { i ->
        PublicKey(ByteArray(32) { if (it == 0) (i + 1).toByte() else 0 }.toList())
    }

    private fun goldenV1() = CoinbaseStableSwapperProgram_Swap(
        pool = goldenKeys[0], inVault = goldenKeys[1], outVault = goldenKeys[2],
        inVaultTokenAccount = goldenKeys[3], outVaultTokenAccount = goldenKeys[4],
        userFromTokenAccount = goldenKeys[5], toTokenAccount = goldenKeys[6],
        feeRecipientTokenAccount = goldenKeys[7], feeRecipient = goldenKeys[8],
        fromMint = goldenKeys[9], toMint = goldenKeys[10],
        user = goldenKeys[11], whitelist = goldenKeys[12],
        amountIn = 1_500_000L, minAmountOut = 1_490_000L,
    ).instruction()

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
    fun swapV2ArgsMatchV1() {
        assertEquals(goldenV1().data.drop(8), goldenV2().data.drop(8))
        assertTrue(goldenV1().data.take(8) != goldenV2().data.take(8))
    }

    @Test
    fun swapV2AccountsAreV1WithoutWhitelist() {
        val v1 = goldenV1()
        val v2 = goldenV2()
        assertEquals(16, v1.accounts.size)
        assertEquals(goldenKeys[12], v1.accounts[12].publicKey)
        assertEquals(15, v2.accounts.size)
        assertEquals(v1.accounts.filterIndexed { i, _ -> i != 12 }, v2.accounts)
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
