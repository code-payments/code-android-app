package com.flipcash.app.tokens.bondingcurve

import com.flipcash.libs.currency.math.CurveTestInitializer
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The acceptance vectors from the shared Android/iOS contract: supply 1.25M tokens, 12,400 held,
 * checked against the real curve tables.
 */
class BondingCurveProjectionTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun initCurve() {
            CurveTestInitializer.initialize()
        }

        private const val WHOLE_TOKEN = 10_000_000_000L
        private const val SUPPLY_QUARKS = 1_250_000L * WHOLE_TOKEN
        private const val HELD_QUARKS = 12_400L * WHOLE_TOKEN
    }

    private val dummyKey = PublicKey.fromBase58("11111111111111111111111111111111")

    private fun token(supplyQuarks: Long = SUPPLY_QUARKS): Token = MintMetadata(
        address = Mint(List(32) { 1.toByte() }),
        decimals = 10,
        name = "TestCoin",
        symbol = "TEST",
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(vm = dummyKey, authority = dummyKey, lockDurationInDays = 21),
        launchpadMetadata = LaunchpadMetadata(
            currencyConfig = dummyKey,
            liquidityPool = dummyKey,
            seed = dummyKey,
            authority = dummyKey,
            mintVault = dummyKey,
            coreMintVault = dummyKey,
            currentCirculatingSupplyQuarks = supplyQuarks,
            sellFeeBps = 100,
            price = Fiat(fiat = 0.01),
            marketCap = Fiat(fiat = 10000.0),
        ),
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    private fun projection(held: Long? = HELD_QUARKS, supply: Long = SUPPLY_QUARKS) =
        BondingCurveProjection(token(supply), supply, held)

    private fun assertWithin(expected: Double, actual: Double, tolerance: Double, label: String) {
        println("VECTOR $label = $actual (expected $expected +/- $tolerance)")
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$label: expected $expected, got $actual")
    }

    @Test
    fun `today vector`() {
        val calc = projection()
        val today = calc.today()
        assertTrue(today.isToday)
        assertWithin(22_700.0, calc.todayReserve.toDouble(), 50.0, "today reserve")
        assertWithin(0.02994, today.price.toDouble(), 0.000005, "today price")
        assertWithin(369.18, today.worth!!.decimalValue, 0.005, "today worth")
    }

    @Test
    fun `fixed tick vectors`() {
        val calc = projection()
        // reserve, price, worth of 12,400 tokens, price/worth display tolerance
        val vectors = listOf(
            Triple(5_000, 0.0144, 177.0),
            Triple(100_000, 0.0977, 1_205.0),
            Triple(1_000_000, 0.887, 10_941.0),
            Triple(10_000_000, 8.78, 108_304.0),
        )
        vectors.forEach { (reserve, price, worth) ->
            val snap = calc.snapshotAtReserve(BigDecimal(reserve))
            assertFalse(snap.isToday)
            assertWithin(price, snap.price.toDouble(), price * 0.005, "price @ $reserve")
            assertWithin(worth, snap.worth!!.decimalValue, 1.0, "worth @ $reserve")
        }
    }

    @Test
    fun `unknown holdings leave worth and shares unknown`() {
        val calc = projection(held = null)
        assertNull(calc.today().worth)
        val ownership = calc.ownership()
        assertNull(ownership.tokensHeld)
        assertNull(ownership.shareOfCirculating)
        assertNull(ownership.shareOfMax)
    }

    @Test
    fun `ownership shares`() {
        val ownership = projection().ownership()
        assertEquals(0, BigDecimal(12_400).compareTo(ownership.tokensHeld))
        assertWithin(0.992, ownership.shareOfCirculating!!.toDouble(), 0.0001, "share of circulating (%)")
        assertWithin(0.0590476, ownership.shareOfMax!!.toDouble(), 0.00001, "share of max (%)")
    }

    @Test
    fun `track spans five thousand to ten million and today sits on it`() {
        val calc = projection()
        assertEquals(0.0, calc.positionOf(BigDecimal(5_000)), 1e-9)
        assertEquals(1.0, calc.positionOf(BigDecimal(10_000_000)), 1e-9)
        assertTrue(calc.todayPosition in 0.0..1.0)
        assertEquals(5, calc.ticks.size)
        assertEquals(calc.ticks.sortedBy { it.position }, calc.ticks)
    }

    @Test
    fun `today beyond the base stops extends the track past it`() {
        // ~$100M reserve: past the $10M tick
        val calc = projection(supply = 12_000_000L * WHOLE_TOKEN)
        assertTrue(calc.todayReserve > BigDecimal(10_000_000))
        assertTrue(calc.todayPosition < 1.0)
        assertTrue(calc.stops.last() > calc.todayReserve)
        assertEquals(0.0, calc.positionOf(BigDecimal(5_000)), 1e-9)
    }

    @Test
    fun `a fixed tick within ten percent of today loses its label`() {
        // Today at exactly the $1M reserve's supply
        val supplyAt1M = projection().snapshotAtReserve(BigDecimal(1_000_000)).supplyQuarks
        val calc = projection(supply = supplyAt1M)
        val millionTick = calc.ticks.single { !it.isToday && it.reserve.compareTo(BigDecimal(1_000_000)) == 0 }
        assertFalse(millionTick.labelVisible)
        // the other fixed ticks keep theirs
        assertTrue(calc.ticks.filter { !it.isToday && it !== millionTick }.all { it.labelVisible })
        assertTrue(calc.ticks.single { it.isToday }.labelVisible)
    }

    @Test
    fun `snapping near today returns today exactly`() {
        val calc = projection()
        val snap = calc.snapshotAt(calc.todayPosition + 0.005)
        assertTrue(snap.isToday)
        assertEquals(SUPPLY_QUARKS, snap.supplyQuarks)
        assertFalse(calc.snapshotAt(calc.todayPosition + 0.2).isToday)
    }

    @Test
    fun `chart spans selected supply plus 750K tokens`() {
        val calc = projection()
        val snap = calc.snapshotAtReserve(BigDecimal(100_000))
        val chart = calc.chart(snap)
        assertEquals(
            maxOf(snap.supply.toDouble(), calc.today().supply.toDouble()) + 750_000.0, chart.xMax, 1e-6,
        )
        assertEquals(0.01, chart.yMin)
        assertEquals(chart.points.last().price, chart.yMax, 1e-12)
        assertTrue(chart.points.any { it.tokens == chart.selectedTokens })
        assertEquals(calc.today().price.toDouble(), chart.todayPrice, 1e-12)
        assertTrue(chart.points.zipWithNext().all { (a, b) -> b.tokens > a.tokens && b.price >= a.price })
    }

    @Test
    fun `holding more than the supply at a low reserve is valued as the whole supply`() {
        val calc = projection(held = 2_000_000L * WHOLE_TOKEN)
        val snap = calc.snapshotAtReserve(BigDecimal(5_000))
        assertWithin(5_000.0, snap.worth!!.decimalValue, 1.0, "whole-supply worth at the reserve")
    }

    @Test
    fun `the fixed slider stops are usd reserves`() {
        val usd = BondingCurveProjection.FIXED_RESERVES
        assertEquals(listOf(5_000, 100_000, 1_000_000, 10_000_000).map { BigDecimal(it) }, usd)
    }

    @Test
    fun `marker, scrub line and today dot stay inside the chart at every slider position`() {
        for (supply in listOf(30_000L, 1_250_000L, 12_000_000L, 20_900_000L)) {
            val calc = projection(supply = supply * WHOLE_TOKEN)
            for (i in 0..200) {
                val position = i / 200.0
                val chart = calc.chart(calc.snapshotAt(position))
                for (tokens in listOf(chart.selectedTokens, chart.todayTokens)) {
                    assertTrue(tokens <= chart.xMax + 1e-6, "supply=$supply pos=$position: $tokens beyond ${chart.xMax}")
                    assertTrue(chart.xFraction(tokens) in 0f..1f)
                }
            }
            for (reserve in listOf(5_000, 100_000, 1_000_000, 10_000_000)) {
                val chart = calc.chart(calc.snapshotAtReserve(BigDecimal(reserve)))
                assertTrue(chart.selectedTokens <= chart.xMax, "supply=$supply reserve=$reserve")
                assertTrue(chart.todayTokens <= chart.xMax, "supply=$supply reserve=$reserve")
            }
        }
    }

    @Test
    fun `x fraction clamps out of range values`() {
        val chart = projection().chart(projection().today())
        assertEquals(1f, chart.xFraction(chart.xMax * 3))
        assertEquals(0f, chart.xFraction(-5.0))
    }

    @Test
    fun `marker x fraction rises with the slider position`() {
        for (supply in listOf(30_000L, 1_250_000L, 12_000_000L)) {
            val calc = projection(supply = supply * WHOLE_TOKEN)
            val fractions = (0..20).map { calc.chart(calc.snapshotAt(it / 20.0)).let { c -> c.xFraction(c.selectedTokens) } }
            assertTrue(fractions.zipWithNext().all { (a, b) -> b > a }, "supply=$supply: $fractions")
        }
    }

    private fun calcAtReserve(reserve: Long): BondingCurveProjection {
        val quarks = projection().snapshotAtReserve(BigDecimal(reserve)).supplyQuarks
        return projection(supply = quarks)
    }

    private fun stopsOf(calc: BondingCurveProjection) = calc.stops.map { it.toLong() }

    private val baseStops = listOf(5_000L, 100_000, 1_000_000, 10_000_000)

    @Test
    fun `small reserves keep the base stops`() {
        assertEquals(baseStops, stopsOf(calcAtReserve(22_700)))
        assertEquals(baseStops, stopsOf(calcAtReserve(171_000)))
        assertEquals(baseStops, stopsOf(projection()))
    }

    @Test
    fun `stops extend by ten times while the top stop is within twice today`() {
        assertEquals(baseStops + 100_000_000L, stopsOf(calcAtReserve(6_000_000)))
        assertEquals(baseStops + 100_000_000L, stopsOf(calcAtReserve(10_000_000)))
        assertEquals(baseStops + 100_000_000L + 1_000_000_000L, stopsOf(calcAtReserve(60_000_000)))
    }

    @Test
    fun `today sits strictly left of the last stop`() {
        for (reserve in listOf(22_700L, 171_000L, 6_000_000L, 8_000_000L, 10_000_000L, 60_000_000L)) {
            val calc = calcAtReserve(reserve)
            assertTrue(calc.todayPosition < 1.0, "reserve=$reserve position=${calc.todayPosition}")
            assertTrue(calc.todayReserve < calc.stops.last(), "reserve=$reserve")
        }
    }

    @Test
    fun `stops are never beyond the reserve at max supply`() {
        val calc = calcAtReserve(60_000_000)
        val cap = com.flipcash.libs.currency.math.Estimator.reserveAtSupply(BigDecimal(21_000_000)).getOrThrow()
        assertTrue(calc.stops.all { it <= cap })
        assertEquals(emptyList(), BondingCurveProjection.reserveStops(BigDecimal(6_000_000), BigDecimal(50_000_000))
            .filter { it > BigDecimal(50_000_000) })
    }

    @Test
    fun `marker stays in bounds across the slider for a 60M reserve`() {
        val calc = calcAtReserve(60_000_000)
        val fractions = (0..100).map { calc.chart(calc.snapshotAt(it / 100.0)).let { c -> c.xFraction(c.selectedTokens) to c } }
        for ((f, c) in fractions) {
            assertTrue(f in 0f..1f)
            assertTrue(c.selectedTokens <= c.xMax && c.todayTokens <= c.xMax)
        }
    }
}
