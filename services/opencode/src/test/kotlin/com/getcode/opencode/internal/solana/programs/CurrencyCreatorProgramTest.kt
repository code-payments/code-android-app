package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyCreatorProgramTest {

    private fun testKey(seed: Int): PublicKey =
        PublicKey(ByteArray(32) { seed.toByte() }.toList())

    // --- Command enum ---

    @Test
    fun commandBuyTokensValue() {
        assertEquals(4.toByte(), CurrencyCreatorProgram.Command.buyTokens.value)
    }

    @Test
    fun commandSellTokensValue() {
        assertEquals(5.toByte(), CurrencyCreatorProgram.Command.sellTokens.value)
    }

    @Test
    fun commandBuyAndDepositValue() {
        assertEquals(6.toByte(), CurrencyCreatorProgram.Command.buyAndDepositIntoVm.value)
    }

    @Test
    fun commandSellAndDepositValue() {
        assertEquals(7.toByte(), CurrencyCreatorProgram.Command.sellAndDepositIntoVm.value)
    }

    // --- BuyAndDepositIntoVm ---

    @Test
    fun buyEncodeStartsWithCommand() {
        val ix = CurrencyCreatorProgram_BuyAndDepositIntoVm(
            inAmount = 1000L, minOutAmount = 900L, vmMemoryIndex = 0,
            buyer = testKey(1), pool = testKey(2),
            targetMint = testKey(3), baseMint = testKey(4),
            vaultTarget = testKey(5), vaultBase = testKey(6),
            buyerBase = testKey(7), vmAuthority = testKey(8),
            vm = testKey(9), vmMemory = testKey(10),
            vmOmnibus = testKey(11), vtaOwner = testKey(12)
        )
        assertEquals(6.toByte(), ix.encode()[0])
    }

    @Test
    fun buyEncodeLength() {
        val ix = CurrencyCreatorProgram_BuyAndDepositIntoVm(
            inAmount = 1000L, minOutAmount = 900L, vmMemoryIndex = 0,
            buyer = testKey(1), pool = testKey(2),
            targetMint = testKey(3), baseMint = testKey(4),
            vaultTarget = testKey(5), vaultBase = testKey(6),
            buyerBase = testKey(7), vmAuthority = testKey(8),
            vm = testKey(9), vmMemory = testKey(10),
            vmOmnibus = testKey(11), vtaOwner = testKey(12)
        )
        // 1 byte command + 8 bytes inAmount + 8 bytes minOutAmount + 2 bytes vmMemoryIndex = 19
        assertEquals(19, ix.encode().size)
    }

    @Test
    fun buyInstructionHas14Accounts() {
        val ix = CurrencyCreatorProgram_BuyAndDepositIntoVm(
            inAmount = 1000L, minOutAmount = 900L, vmMemoryIndex = 0,
            buyer = testKey(1), pool = testKey(2),
            targetMint = testKey(3), baseMint = testKey(4),
            vaultTarget = testKey(5), vaultBase = testKey(6),
            buyerBase = testKey(7), vmAuthority = testKey(8),
            vm = testKey(9), vmMemory = testKey(10),
            vmOmnibus = testKey(11), vtaOwner = testKey(12)
        )
        assertEquals(14, ix.instruction().accounts.size)
    }

    @Test
    fun buyInstructionProgram() {
        val ix = CurrencyCreatorProgram_BuyAndDepositIntoVm(
            inAmount = 1000L, minOutAmount = 900L, vmMemoryIndex = 0,
            buyer = testKey(1), pool = testKey(2),
            targetMint = testKey(3), baseMint = testKey(4),
            vaultTarget = testKey(5), vaultBase = testKey(6),
            buyerBase = testKey(7), vmAuthority = testKey(8),
            vm = testKey(9), vmMemory = testKey(10),
            vmOmnibus = testKey(11), vtaOwner = testKey(12)
        )
        assertEquals(CurrencyCreatorProgram.address, ix.instruction().program)
    }

    // --- SellAndDepositIntoVm ---

    @Test
    fun sellEncodeStartsWithCommand() {
        val ix = CurrencyCreatorProgram_SellAndDepositIntoVm(
            inAmount = 500L, minOutAmount = 400L, vmMemoryIndex = 1,
            seller = testKey(1), pool = testKey(2),
            targetMint = testKey(3), baseMint = testKey(4),
            vaultTarget = testKey(5), vaultBase = testKey(6),
            sellerTarget = testKey(7), vmAuthority = testKey(8),
            vm = testKey(9), vmMemory = testKey(10),
            vmOmnibus = testKey(11), vtaOwner = testKey(12)
        )
        assertEquals(7.toByte(), ix.encode()[0])
    }

    @Test
    fun sellEncodeLength() {
        val ix = CurrencyCreatorProgram_SellAndDepositIntoVm(
            inAmount = 500L, minOutAmount = 400L, vmMemoryIndex = 1,
            seller = testKey(1), pool = testKey(2),
            targetMint = testKey(3), baseMint = testKey(4),
            vaultTarget = testKey(5), vaultBase = testKey(6),
            sellerTarget = testKey(7), vmAuthority = testKey(8),
            vm = testKey(9), vmMemory = testKey(10),
            vmOmnibus = testKey(11), vtaOwner = testKey(12)
        )
        // 1 byte command + 8 + 8 + 2 = 19
        assertEquals(19, ix.encode().size)
    }

    @Test
    fun sellInstructionHas14Accounts() {
        val ix = CurrencyCreatorProgram_SellAndDepositIntoVm(
            inAmount = 500L, minOutAmount = 400L, vmMemoryIndex = 1,
            seller = testKey(1), pool = testKey(2),
            targetMint = testKey(3), baseMint = testKey(4),
            vaultTarget = testKey(5), vaultBase = testKey(6),
            sellerTarget = testKey(7), vmAuthority = testKey(8),
            vm = testKey(9), vmMemory = testKey(10),
            vmOmnibus = testKey(11), vtaOwner = testKey(12)
        )
        assertEquals(14, ix.instruction().accounts.size)
    }

    @Test
    fun sellVmMemoryIndexEncodedAsU16() {
        val ix = CurrencyCreatorProgram_SellAndDepositIntoVm(
            inAmount = 0L, minOutAmount = 0L, vmMemoryIndex = 0x0102,
            seller = testKey(1), pool = testKey(2),
            targetMint = testKey(3), baseMint = testKey(4),
            vaultTarget = testKey(5), vaultBase = testKey(6),
            sellerTarget = testKey(7), vmAuthority = testKey(8),
            vm = testKey(9), vmMemory = testKey(10),
            vmOmnibus = testKey(11), vtaOwner = testKey(12)
        )
        val encoded = ix.encode()
        // Last 2 bytes should be little-endian 0x0102 → [0x02, 0x01]
        assertEquals(0x02.toByte(), encoded[encoded.size - 2])
        assertEquals(0x01.toByte(), encoded[encoded.size - 1])
    }
}
