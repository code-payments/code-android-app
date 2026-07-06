package com.flipcash.app.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class GrabBillPropertiesTest {

    @Test
    fun grabBillEmitsGrabTimeAndStageProperties() {
        val event = AnalyticsEvent.GrabBill(
            time = 350,
            stages = linkedMapOf("nativeScan" to 12L, "pollForGiveRequest" to 120L),
        )
        val props = event.toProperties()
        assertEquals("350", props["Grab Time"])
        assertEquals("12", props["nativeScan"])
        assertEquals("120", props["pollForGiveRequest"])
    }

    @Test
    fun grabBillWithNoStagesStillEmitsGrabTime() {
        val event = AnalyticsEvent.GrabBill(time = 350, stages = emptyMap())
        assertEquals("350", event.toProperties()["Grab Time"])
        assertEquals(1, event.toProperties().size)
    }

    @Test
    fun grabBillWithNullTimeOmitsGrabTimeButKeepsStages() {
        val event = AnalyticsEvent.GrabBill(time = null, stages = mapOf("nativeScan" to 12L))
        val props = event.toProperties()
        assertEquals(null, props["Grab Time"])
        assertEquals("12", props["nativeScan"])
    }
}
