package com.flipcash.app.messenger.internal.request

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A collection's "Select Payment Currency" rows (frame 6.3): the held currencies, plus the
 * paying one even when the payer holds none of it (group-collections spec, "Default currency").
 */
class CollectionCurrenciesTest {

    private val usdRate = Rate(fx = 1.0, currency = CurrencyCode.USD)

    private fun token(name: String, seed: Byte): Token = mockk(relaxed = true) {
        every { address } returns Mint(List(32) { seed })
        every { this@mockk.name } returns name
    }

    private val jeffy = token("Jeffy", 7)
    private val bogey = token("Bogey", 8)

    private fun held(token: Token, usd: Double) = TokenWithBalance(token, Fiat(usd, CurrencyCode.USD))

    @Test
    fun `the paying currency is listed at a zero balance`() {
        val rows = collectionCurrencies(
            balances = listOf(held(Token.usdf, 8.0), held(jeffy, 0.0)),
            paying = jeffy,
            rate = usdRate,
        )

        assertEquals(listOf(Token.usdf.address, jeffy.address), rows.map { it.token.address })
    }

    @Test
    fun `a zero balance that is not the paying currency is left out`() {
        val rows = collectionCurrencies(
            balances = listOf(held(Token.usdf, 8.0), held(jeffy, 6.0), held(bogey, 0.0)),
            paying = Token.usdf,
            rate = usdRate,
        )

        assertEquals(listOf(Token.usdf.address, jeffy.address), rows.map { it.token.address })
    }

    @Test
    fun `a held paying currency is listed once, in balance order`() {
        val rows = collectionCurrencies(
            balances = listOf(held(jeffy, 6.0), held(Token.usdf, 8.0), held(bogey, 12.0)),
            paying = jeffy,
            rate = usdRate,
        )

        assertEquals(
            listOf(bogey.address, Token.usdf.address, jeffy.address),
            rows.map { it.token.address },
        )
    }

    @Test
    fun `with no paying currency only held balances are listed`() {
        val rows = collectionCurrencies(
            balances = listOf(held(jeffy, 0.0)),
            paying = null,
            rate = usdRate,
        )

        assertEquals(emptyList<Mint>(), rows.map { it.token.address })
    }
}
