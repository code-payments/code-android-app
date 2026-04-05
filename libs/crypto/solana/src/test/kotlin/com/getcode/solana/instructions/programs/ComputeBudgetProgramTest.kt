package com.getcode.solana.instructions.programs

import kotlin.test.Test
import kotlin.test.assertEquals

class ComputeBudgetProgramTest {

    // --- SetComputeUnitPrice ---

    @Test
    fun unitPriceEncodeDecodeRoundtrip() {
        val original = ComputeBudgetProgram_SetComputeUnitPrice(microLamports = 50000L, bump = 0)
        val instruction = original.instruction()
        val decoded = ComputeBudgetProgram_SetComputeUnitPrice.newInstance(instruction)
        assertEquals(50000L, decoded.microLamports)
    }

    @Test
    fun unitPriceZero() {
        val original = ComputeBudgetProgram_SetComputeUnitPrice(microLamports = 0L, bump = 0)
        val instruction = original.instruction()
        val decoded = ComputeBudgetProgram_SetComputeUnitPrice.newInstance(instruction)
        assertEquals(0L, decoded.microLamports)
    }

    @Test
    fun unitPriceLargeValue() {
        val original = ComputeBudgetProgram_SetComputeUnitPrice(microLamports = 1_000_000_000L, bump = 0)
        val instruction = original.instruction()
        val decoded = ComputeBudgetProgram_SetComputeUnitPrice.newInstance(instruction)
        assertEquals(1_000_000_000L, decoded.microLamports)
    }

    @Test
    fun unitPriceEncodeStartsWithCommandByte() {
        val price = ComputeBudgetProgram_SetComputeUnitPrice(microLamports = 100, bump = 0)
        val encoded = price.encode()
        assertEquals(ComputeBudgetProgram.Command.setComputeUnitPrice.ordinal.toByte(), encoded[0])
    }

    @Test
    fun unitPriceEncodeHasCorrectLength() {
        val price = ComputeBudgetProgram_SetComputeUnitPrice(microLamports = 100, bump = 0)
        val encoded = price.encode()
        // 1 byte command + 8 bytes Long
        assertEquals(9, encoded.size)
    }

    // --- SetComputeUnitLimit ---

    @Test
    fun unitLimitEncodeDecodeRoundtrip() {
        val original = ComputeBudgetProgram_SetComputeUnitLimit(limit = 200_000, bump = 0)
        val instruction = original.instruction()
        val decoded = ComputeBudgetProgram_SetComputeUnitLimit.newInstance(instruction)
        assertEquals(200_000, decoded.limit)
    }

    @Test
    fun unitLimitZero() {
        val original = ComputeBudgetProgram_SetComputeUnitLimit(limit = 0, bump = 0)
        val instruction = original.instruction()
        val decoded = ComputeBudgetProgram_SetComputeUnitLimit.newInstance(instruction)
        assertEquals(0, decoded.limit)
    }

    @Test
    fun unitLimitMaxValue() {
        val original = ComputeBudgetProgram_SetComputeUnitLimit(limit = 1_400_000, bump = 0)
        val instruction = original.instruction()
        val decoded = ComputeBudgetProgram_SetComputeUnitLimit.newInstance(instruction)
        assertEquals(1_400_000, decoded.limit)
    }

    @Test
    fun unitLimitEncodeStartsWithCommandByte() {
        val limit = ComputeBudgetProgram_SetComputeUnitLimit(limit = 100, bump = 0)
        val encoded = limit.encode()
        assertEquals(ComputeBudgetProgram.Command.setComputeUnitLimit.ordinal.toByte(), encoded[0])
    }

    @Test
    fun unitLimitEncodeHasCorrectLength() {
        val limit = ComputeBudgetProgram_SetComputeUnitLimit(limit = 100, bump = 0)
        val encoded = limit.encode()
        // 1 byte command + 4 bytes Int
        assertEquals(5, encoded.size)
    }

    // --- Command enum ---

    @Test
    fun commandValues() {
        assertEquals(0.toByte(), ComputeBudgetProgram.Command.requestUnits.value)
        assertEquals(1.toByte(), ComputeBudgetProgram.Command.requestHeapFrame.value)
        assertEquals(2.toByte(), ComputeBudgetProgram.Command.setComputeUnitLimit.value)
        assertEquals(3.toByte(), ComputeBudgetProgram.Command.setComputeUnitPrice.value)
    }
}
