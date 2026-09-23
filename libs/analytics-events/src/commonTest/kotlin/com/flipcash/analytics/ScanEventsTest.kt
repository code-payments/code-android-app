package com.flipcash.analytics

import com.flipcash.analytics.events.ScanEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class ScanEventsTest {

    @Test
    fun propertylessEvents() {
        assertEquals(AnalyticsEvent("Gallery Scan: Image Picked", emptyMap()), ScanEvents.galleryImagePicked())
        assertEquals(AnalyticsEvent("Tip Card Scanned", emptyMap()), ScanEvents.tipCardScanned())
        assertEquals(AnalyticsEvent("Tip Card Presented", emptyMap()), ScanEvents.tipCardPresented())
    }

    @Test
    fun gallerySucceededCarriesNumbers() {
        assertEquals(
            AnalyticsEvent(
                "Gallery Scan: Succeeded",
                mapOf(
                    "Tier" to PropertyValue.Number(2.0),
                    "Zoom" to PropertyValue.Number(1.5),
                    "Time" to PropertyValue.Number(840.0),
                ),
            ),
            ScanEvents.gallerySucceeded(2, 1.5, 840),
        )
    }

    @Test
    fun galleryFailedCarriesExhaustedAsAFlag() {
        assertEquals(
            AnalyticsEvent(
                "Gallery Scan: Failed",
                mapOf("Time" to PropertyValue.Number(1500.0), "Exhausted" to PropertyValue.Flag(true)),
            ),
            ScanEvents.galleryFailed(1500, true),
        )
    }
}
