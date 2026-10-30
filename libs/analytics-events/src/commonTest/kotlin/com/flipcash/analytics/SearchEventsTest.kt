package com.flipcash.analytics

import com.flipcash.analytics.events.SearchEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchEventsTest {

    @Test
    fun openedCarriesNoProperties() {
        assertEquals(AnalyticsEvent("Search: Opened", emptyMap()), SearchEvents.opened())
    }

    @Test
    fun resultTappedCarriesEveryResultTypeByItsWireString() {
        assertEquals(
            listOf("Chat", "Currency", "Message", "Link"),
            SearchResultType.entries.map {
                val event = SearchEvents.resultTapped(it)
                assertEquals(AnalyticsEvent("Search: Result Tapped", mapOf("Result Type" to event.properties.getValue("Result Type"))), event)
                (event.properties.getValue("Result Type") as PropertyValue.Text).value
            },
        )
    }
}
