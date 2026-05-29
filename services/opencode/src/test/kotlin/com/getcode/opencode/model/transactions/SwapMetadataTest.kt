package com.getcode.opencode.model.transactions

import kotlin.test.Test
import kotlin.test.assertEquals

class SwapMetadataTest {

    @Test
    fun `SwapState has 9 entries`() {
        assertEquals(9, SwapState.entries.size)
    }

    @Test
    fun `ordinal 0 is UNKNOWN`() {
        assertEquals(SwapState.UNKNOWN, SwapState.entries[0])
    }

    @Test
    fun `ordinal 1 is CREATED`() {
        assertEquals(SwapState.CREATED, SwapState.entries[1])
    }

    @Test
    fun `ordinal 5 is FINALIZED`() {
        assertEquals(SwapState.FINALIZED, SwapState.entries[5])
    }

    @Test
    fun `ordinal 8 is CANCELLED`() {
        assertEquals(SwapState.CANCELLED, SwapState.entries[8])
    }

    @Test
    fun `ordinal values match expected order`() {
        val expected = listOf(
            SwapState.UNKNOWN,
            SwapState.CREATED,
            SwapState.FUNDING,
            SwapState.FUNDED,
            SwapState.SUBMITTING,
            SwapState.FINALIZED,
            SwapState.FAILED,
            SwapState.CANCELLING,
            SwapState.CANCELLED,
        )
        assertEquals(expected, SwapState.entries.toList())
    }

    @Test
    fun `entries getOrElse returns UNKNOWN for negative index`() {
        val result = SwapState.entries.getOrElse(-1) { SwapState.UNKNOWN }
        assertEquals(SwapState.UNKNOWN, result)
    }

    @Test
    fun `entries getOrElse returns UNKNOWN for out of bounds index`() {
        val result = SwapState.entries.getOrElse(99) { SwapState.UNKNOWN }
        assertEquals(SwapState.UNKNOWN, result)
    }
}
