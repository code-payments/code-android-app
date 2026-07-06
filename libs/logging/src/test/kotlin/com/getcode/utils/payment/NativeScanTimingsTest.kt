package com.getcode.utils.payment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeScanTimingsTest {

    @kotlin.test.AfterTest
    fun drain() { NativeScanTimings.consumeLatest() }

    @Test
    fun consumeReturnsLastRecordedThenClears() {
        NativeScanTimings.record(NativeScanTimings.Sample(durationMs = 12, hit = true, width = 1920, height = 1080, quality = 10))

        val first = NativeScanTimings.consumeLatest()
        assertEquals(12L, first?.durationMs)
        assertEquals(true, first?.hit)

        assertNull(NativeScanTimings.consumeLatest())
    }

    @Test
    fun laterRecordOverwritesEarlier() {
        NativeScanTimings.record(NativeScanTimings.Sample(1, false, 1, 1, 0))
        NativeScanTimings.record(NativeScanTimings.Sample(2, true, 1, 1, 0))
        assertEquals(2L, NativeScanTimings.consumeLatest()?.durationMs)
    }
}
