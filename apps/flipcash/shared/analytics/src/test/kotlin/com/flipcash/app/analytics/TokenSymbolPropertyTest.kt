package com.flipcash.app.analytics

import com.flipcash.app.analytics.internal.withTokenSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TokenSymbolPropertyTest {

    private val resolver = TokenSymbolResolver { mint ->
        when (mint) {
            "MintA" -> "AAA"
            "MintB" -> "BBB"
            else -> null
        }
    }

    @Test
    fun `adds Token Symbol beside Mint`() {
        val result = listOf("Mint" to "MintA").withTokenSymbols(resolver).toMap()
        assertEquals("AAA", result["Token Symbol"])
        assertEquals("MintA", result["Mint"])
    }

    @Test
    fun `adds Payment Token Symbol beside Payment Mint`() {
        val result = listOf("Payment Mint" to "MintB").withTokenSymbols(resolver).toMap()
        assertEquals("BBB", result["Payment Token Symbol"])
    }

    @Test
    fun `omits the property entirely when the mint is unknown`() {
        val result = listOf("Mint" to "MintZ").withTokenSymbols(resolver).toMap()
        // Absent, not empty — a failed lookup must be distinguishable from a
        // token that genuinely has no symbol.
        assertFalse(result.containsKey("Token Symbol"))
    }

    @Test
    fun `leaves properties without a mint untouched`() {
        val input = listOf("Chat Type" to "Tip", "Fiat" to "5.0")
        assertEquals(input, input.withTokenSymbols(resolver))
    }

    @Test
    fun `does not overwrite a symbol the event already supplied`() {
        val result = listOf("Mint" to "MintA", "Token Symbol" to "EXPLICIT")
            .withTokenSymbols(resolver).toMap()
        assertEquals("EXPLICIT", result["Token Symbol"])
    }

    @Test
    fun `resolves both mints when an event carries both`() {
        val result = listOf("Mint" to "MintA", "Payment Mint" to "MintB")
            .withTokenSymbols(resolver).toMap()
        assertEquals("AAA", result["Token Symbol"])
        assertEquals("BBB", result["Payment Token Symbol"])
    }
}
