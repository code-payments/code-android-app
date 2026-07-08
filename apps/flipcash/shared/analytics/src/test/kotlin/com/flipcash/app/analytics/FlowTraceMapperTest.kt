package com.flipcash.app.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlowTraceMapperTest {

    @Test
    fun `grab bill maps to grab_flow with stage metrics plus total`() {
        val stages = mapOf("nativeScan" to 8L, "pollGiveRequest" to 120L)
        val trace = Analytics.Transfer.GrabBill(time = 200, stages = stages).flowTrace()
        requireNotNull(trace)
        assertEquals("grab_flow", trace.name)
        assertEquals(8L, trace.metrics["nativeScan"])
        assertEquals(120L, trace.metrics["pollGiveRequest"])
        assertEquals(128L, trace.metrics["total_ms"])
    }

    @Test
    fun `give bill maps to give_flow`() {
        val trace = Analytics.Transfer.GiveBill(stages = mapOf("submitIntent" to 50L)).flowTrace()
        requireNotNull(trace)
        assertEquals("give_flow", trace.name)
        assertEquals(50L, trace.metrics["total_ms"])
    }

    @Test
    fun `give bill with no stages produces no trace`() {
        assertNull(Analytics.Transfer.GiveBill().flowTrace())
    }

    @Test
    fun `non-flow transfer events produce no trace`() {
        assertNull(Analytics.Transfer.Initiate.GiveBillStart.flowTrace())
    }
}
