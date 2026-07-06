package com.getcode.utils.payment

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PaymentTraceRegistryTest {

    @AfterTest
    fun cleanup() {
        PaymentTraceRegistry.clearForTest()
    }

    @Test
    fun openThenGetReturnsSameTrace() {
        val opened = PaymentTraceRegistry.open("rv1", PaymentFlow.Grab)
        val fetched = PaymentTraceRegistry.get("rv1")
        assertSame(opened, fetched)
    }

    @Test
    fun openReplacesAnExistingTraceForSameCorrelationId() {
        val first = PaymentTraceRegistry.open("rv1", PaymentFlow.Grab)
        val second = PaymentTraceRegistry.open("rv1", PaymentFlow.Grab)
        assertSame(second, PaymentTraceRegistry.get("rv1"))
        assert(first !== PaymentTraceRegistry.get("rv1"))
    }

    @Test
    fun finishReturnsTraceAndEvicts() {
        val opened = PaymentTraceRegistry.open("rv1", PaymentFlow.Grab)
        opened.mark("stageA", 5)

        val finished = PaymentTraceRegistry.finish("rv1", success = true)

        assertSame(opened, finished)
        assertNull(PaymentTraceRegistry.get("rv1"))
    }

    @Test
    fun getUnknownReturnsNull() {
        assertNull(PaymentTraceRegistry.get("nope"))
    }

    @Test
    fun finishUnknownReturnsNull() {
        assertNull(PaymentTraceRegistry.finish("nope", success = false))
    }

    @Test
    fun evictsOldestWhenOverCapacity() {
        repeat(PaymentTraceRegistry.MAX_IN_FLIGHT + 5) { i ->
            PaymentTraceRegistry.open("rv$i", PaymentFlow.Grab)
        }
        assertNull(PaymentTraceRegistry.get("rv0"))
    }

    @Test
    fun summaryMetadataIncludesStageDurations() {
        val trace = PaymentTraceRegistry.open("rv1", PaymentFlow.Give)
        trace.mark("nativeScan", 12)
        trace.mark("pollForGiveRequest", 120)

        val meta = PaymentTraceRegistry.summaryMetadata(trace, success = true)

        assertEquals(12L, meta["nativeScan"])
        assertEquals(120L, meta["pollForGiveRequest"])
        assertEquals("Give", meta["flow"])
        assertEquals("rv1", meta["correlationId"])
        assertEquals(132L, meta["totalMs"])
    }
}
