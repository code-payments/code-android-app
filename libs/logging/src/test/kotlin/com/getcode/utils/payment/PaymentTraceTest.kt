package com.getcode.utils.payment

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentTraceTest {

    @Test
    fun spanRecordsNamedDurationAndReturnsBlockValue() {
        val trace = PaymentTrace(correlationId = "rv1", flow = PaymentFlow.Grab)

        val result = trace.span("stageA") { 42 }

        assertEquals(42, result)
        assertTrue(trace.durations().containsKey("stageA"))
        assertTrue(trace.durations()["stageA"]!! >= 0L)
    }

    @Test
    fun spanRecordsFailureWhenBlockThrows() {
        val trace = PaymentTrace(correlationId = "rv1", flow = PaymentFlow.Grab)

        runCatching { trace.span<Int>("boom") { throw IllegalStateException("x") } }

        val span = trace.spans().single { it.name == "boom" }
        assertFalse(span.success)
        assertEquals("x", span.error?.message)
    }

    @Test
    fun markRecordsExternallyMeasuredDuration() {
        val trace = PaymentTrace(correlationId = "rv1", flow = PaymentFlow.Grab)

        trace.mark("nativeScan", durationMs = 17, metadata = mapOf("hit" to true))

        assertEquals(17L, trace.durations()["nativeScan"])
        assertEquals(true, trace.spans().single { it.name == "nativeScan" }.metadata["hit"])
    }

    @Test
    fun nullSafeSpanExtensionRunsBlockWhenTraceIsNull() {
        val trace: PaymentTrace? = null

        val result = trace.spanOrRun("stageA") { 7 }

        assertEquals(7, result)
    }

    @Test
    fun spanSuspendRecordsDurationAndReturnsValue() = runBlocking {
        val trace = PaymentTrace(correlationId = "rv1", flow = PaymentFlow.Give)

        val result = trace.spanSuspend("suspendStage") { 99 }

        assertEquals(99, result)
        assertTrue(trace.durations().containsKey("suspendStage"))
        assertTrue(trace.durations()["suspendStage"]!! >= 0L)
    }

    @Test
    fun totalMsIsSumOfRecordedDurations() {
        val trace = PaymentTrace(correlationId = "rv1", flow = PaymentFlow.Give)

        trace.mark("a", durationMs = 10)
        trace.mark("b", durationMs = 15)

        assertEquals(25L, trace.totalMs)
    }
}
