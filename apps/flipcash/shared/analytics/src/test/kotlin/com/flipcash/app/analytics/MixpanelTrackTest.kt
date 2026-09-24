package com.flipcash.app.analytics

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.PropertyValue
import com.flipcash.app.analytics.internal.toMixpanelProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MixpanelTrackTest {

    private val resolver = TokenSymbolResolver { mint ->
        when (mint) {
            "MintA" -> "AAA"
            "MintB" -> "BBB"
            else -> null
        }
    }

    @Test
    fun `maps each property type to its JSON type`() {
        val event = AnalyticsEvent(
            name = "Test",
            properties = mapOf(
                "Text" to PropertyValue.Text("hello"),
                "Number" to PropertyValue.Number(25_000_000.0),
                "Flag" to PropertyValue.Flag(true),
            ),
        )

        val result = event.toMixpanelProperties(resolver)

        assertEquals(mapOf("Text" to "hello", "Number" to 25_000_000.0, "Flag" to true), result)
    }

    @Test
    fun `adds Token Symbol beside Mint`() {
        val event = AnalyticsEvent("Test", mapOf("Mint" to PropertyValue.Text("MintA")))

        val result = event.toMixpanelProperties(resolver)

        assertEquals(mapOf("Mint" to "MintA", "Token Symbol" to "AAA"), result)
    }

    @Test
    fun `adds Payment Token Symbol beside Payment Mint`() {
        val event = AnalyticsEvent(
            "Test",
            mapOf("Mint" to PropertyValue.Text("MintA"), "Payment Mint" to PropertyValue.Text("MintB")),
        )

        val result = event.toMixpanelProperties(resolver)

        assertEquals("AAA", result["Token Symbol"])
        assertEquals("BBB", result["Payment Token Symbol"])
    }

    @Test
    fun `adds nothing when the mint is unknown`() {
        val event = AnalyticsEvent("Test", mapOf("Mint" to PropertyValue.Text("MintZ")))

        val result = event.toMixpanelProperties(resolver)

        assertFalse(result.containsKey("Token Symbol"))
    }

    @Test
    fun `does not overwrite a symbol the event already supplied`() {
        val event = AnalyticsEvent(
            "Test",
            mapOf("Mint" to PropertyValue.Text("MintA"), "Token Symbol" to PropertyValue.Text("EXPLICIT")),
        )

        val result = event.toMixpanelProperties(resolver)

        assertEquals("EXPLICIT", result["Token Symbol"])
    }
}
