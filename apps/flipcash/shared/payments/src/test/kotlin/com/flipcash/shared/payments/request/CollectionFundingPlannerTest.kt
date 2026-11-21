package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.CollectionFundingPlan
import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The $5.00 minimum and the order's cents across currencies (group-collections spec, "Funding a payment"). */
class CollectionFundingPlannerTest {

    private val planner = CollectionFundingPlanner(mockk<TokenCoordinator>(), mockk<Exchange>())

    private fun plan(topUpMicros: Long) = CollectionFundingPlan(
        payingCurrency = "usdf",
        shortfallMicros = topUpMicros,
        topUpMicros = topUpMicros,
        legs = emptyList(),
    )

    @Test
    fun `the minimum is five dollars in USD`() {
        assertEquals(5_000_000, CollectionFundingPlanner.minTopUpMicros(Rate(1.0, CurrencyCode.USD), minorDigits = 2))
    }

    @Test
    fun `the minimum converts into the request currency, rounded up to its minor unit`() {
        // 5 × 1.3712 = 6.856, up to the cent.
        assertEquals(6_860_000, CollectionFundingPlanner.minTopUpMicros(Rate(1.3712, CurrencyCode.CAD), minorDigits = 2))
        // 5 × 150.123 = 750.615 yen, up to the yen.
        assertEquals(751_000_000, CollectionFundingPlanner.minTopUpMicros(Rate(150.123, CurrencyCode.JPY), minorDigits = 0))
    }

    @Test
    fun `a USD top-up orders its exact cents`() {
        assertEquals(1204, planner.topUpUsdCents(plan(12_040_000), Rate(1.0, CurrencyCode.USD)))
        assertEquals(500, planner.topUpUsdCents(plan(5_000_000), Rate(1.0, CurrencyCode.USD)))
    }

    @Test
    fun `another currency's top-up converts back to USD, rounded up to the cent`() {
        // C$10.01 / 1.37 = $7.306569…, up to $7.31.
        assertEquals(731, planner.topUpUsdCents(plan(10_010_000), Rate(1.37, CurrencyCode.CAD)))
        // C$6.85 / 1.37 is exactly $5.00.
        assertEquals(500, planner.topUpUsdCents(plan(6_850_000), Rate(1.37, CurrencyCode.CAD)))
    }

    @Test
    fun `a plan without a top-up orders nothing`() {
        assertNull(planner.topUpUsdCents(plan(0), Rate(1.0, CurrencyCode.USD)))
    }
}
