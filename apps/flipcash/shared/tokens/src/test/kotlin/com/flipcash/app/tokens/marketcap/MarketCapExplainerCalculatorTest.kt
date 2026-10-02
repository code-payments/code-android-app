package com.flipcash.app.tokens.marketcap

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
class MarketCapExplainerCalculatorTest {

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

    private fun calculator(held: Long? = HELD_QUARKS, supply: Long = SUPPLY_QUARKS) =
        MarketCapExplainerCalculator(token(supply), supply, held)

    private fun assertWithin(expected: Double, actual: Double, tolerance: Double, label: String) {
        println("VECTOR $label = $actual (expected $expected +/- $tolerance)")
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$label: expected $expected, got $actual")
    }

    @Test
    fun `today vector`() {
        val calc = calculator()
        val today = calc.today()
        assertTrue(today.isToday)
        assertWithin(22_700.0, calc.todayReserve.toDouble(), 50.0, "today reserve")
        assertWithin(0.02994, today.price.toDouble(), 0.000005, "today price")
        assertWithin(369.18, today.worth!!.decimalValue, 0.005, "today worth")
        assertEquals("$0.02994", ExplainerCurrency(Rate.oneToOne).price(today.price))
        assertEquals("$22.7K", ExplainerCurrency(Rate.oneToOne).reserve(calc.todayReserve))
    }

    @Test
    fun `fixed tick vectors`() {
        val calc = calculator()
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
        val calc = calculator(held = null)
        assertNull(calc.today().worth)
        val ownership = calc.ownership()
        assertNull(ownership.tokensHeld)
        assertNull(ownership.shareOfCirculating)
        assertNull(ownership.shareOfMax)
    }

    @Test
    fun `ownership shares`() {
        val ownership = calculator().ownership()
        assertEquals(0, BigDecimal(12_400).compareTo(ownership.tokensHeld))
        assertWithin(0.992, ownership.shareOfCirculating!!.toDouble(), 0.0001, "share of circulating (%)")
        assertWithin(0.0590476, ownership.shareOfMax!!.toDouble(), 0.00001, "share of max (%)")
        assertEquals("0.99%", ExplainerFormat.percent(ownership.shareOfCirculating!!))
    }

    @Test
    fun `track spans five thousand to ten million and today sits on it`() {
        val calc = calculator()
        assertEquals(0.0, calc.positionOf(BigDecimal(5_000)), 1e-9)
        assertEquals(1.0, calc.positionOf(BigDecimal(10_000_000)), 1e-9)
        assertTrue(calc.todayPosition in 0.0..1.0)
        assertEquals(5, calc.ticks.size)
        assertEquals(calc.ticks.sortedBy { it.position }, calc.ticks)
    }

    @Test
    fun `today beyond the base stops extends the track past it`() {
        // ~$100M reserve: past the $10M tick
        val calc = calculator(supply = 12_000_000L * WHOLE_TOKEN)
        assertTrue(calc.todayReserve > BigDecimal(10_000_000))
        assertTrue(calc.todayPosition < 1.0)
        assertTrue(calc.stops.last() > calc.todayReserve)
        assertEquals(0.0, calc.positionOf(BigDecimal(5_000)), 1e-9)
    }

    @Test
    fun `a fixed tick within ten percent of today loses its label`() {
        // Today at exactly the $1M reserve's supply
        val supplyAt1M = calculator().snapshotAtReserve(BigDecimal(1_000_000)).supplyQuarks
        val calc = calculator(supply = supplyAt1M)
        val millionTick = calc.ticks.single { !it.isToday && it.reserve.compareTo(BigDecimal(1_000_000)) == 0 }
        assertFalse(millionTick.labelVisible)
        // the other fixed ticks keep theirs
        assertTrue(calc.ticks.filter { !it.isToday && it !== millionTick }.all { it.labelVisible })
        assertTrue(calc.ticks.single { it.isToday }.labelVisible)
    }

    @Test
    fun `snapping near today returns today exactly`() {
        val calc = calculator()
        val snap = calc.snapshotAt(calc.todayPosition + 0.005)
        assertTrue(snap.isToday)
        assertEquals(SUPPLY_QUARKS, snap.supplyQuarks)
        assertFalse(calc.snapshotAt(calc.todayPosition + 0.2).isToday)
    }

    @Test
    fun `chart spans selected supply plus 750K tokens`() {
        val calc = calculator()
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
        val calc = calculator(held = 2_000_000L * WHOLE_TOKEN)
        val snap = calc.snapshotAtReserve(BigDecimal(5_000))
        assertWithin(5_000.0, snap.worth!!.decimalValue, 1.0, "whole-supply worth at the reserve")
    }

    @Test
    fun `display converts to the preferred currency while the curve stays in usd`() {
        val euro = ExplainerCurrency(Rate(fx = 0.5, currency = CurrencyCode.EUR))
        // Reserve labels: 5,000 USD is 2,500 EUR; 10M USD is 5M EUR.
        assertEquals("\u20ac2.5K", euro.reserve(BigDecimal(5_000)))
        assertEquals("\u20ac50K", euro.reserve(BigDecimal(100_000)))
        assertEquals("\u20ac500K", euro.reserve(BigDecimal(1_000_000)))
        assertEquals("\u20ac5M", euro.reserve(BigDecimal(10_000_000)))
        // Per-token price: 0.02994 USD is 0.01497 EUR, kept to four significant figures.
        assertEquals("\u20ac0.01497", euro.price(BigDecimal("0.02994")))
        // Worth: 369.18 USD is 184.59 EUR.
        assertEquals(CurrencyCode.EUR, euro.convert(Fiat(369.18)).currencyCode)
        assertEquals("\u20ac184.59", euro.convert(Fiat(369.18)).formatted())

        // The slider stops are USD reserves regardless of the rate.
        val usd = MarketCapExplainerCalculator.FIXED_RESERVES
        assertEquals(listOf(5_000, 100_000, 1_000_000, 10_000_000).map { BigDecimal(it) }, usd)
    }

    @Test
    fun `a zero-decimal currency keeps whole units and rolls over compact suffixes`() {
        val yen = ExplainerCurrency(Rate(fx = 150.0, currency = CurrencyCode.JPY))
        assertEquals("\u00a5750K", yen.reserve(BigDecimal(5_000)))
        assertEquals("\u00a51.5B", yen.reserve(BigDecimal(10_000_000)))
        // 999,960 rounds to 1000K, which must read 1M.
        assertEquals("\u00a51M", ExplainerCurrency(Rate.oneToOne.copy(currency = CurrencyCode.JPY)).reserve(BigDecimal(999_960)))
        assertEquals("\u00a54.491", yen.price(BigDecimal("0.02994")))
    }

    @Test
    fun `marker, scrub line and today dot stay inside the chart at every slider position`() {
        for (supply in listOf(30_000L, 1_250_000L, 12_000_000L, 20_900_000L)) {
            val calc = calculator(supply = supply * WHOLE_TOKEN)
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
        val chart = calculator().chart(calculator().today())
        assertEquals(1f, chart.xFraction(chart.xMax * 3))
        assertEquals(0f, chart.xFraction(-5.0))
    }

    @Test
    fun `a tiny non-zero share reads as less than a hundredth of a percent`() {
        val share = BigDecimal(597).multiply(BigDecimal(100)).divide(BigDecimal(21_000_000), java.math.MathContext(20))
        assertEquals("<0.01%", ExplainerFormat.percent(share))
        assertEquals("<0.01%", ExplainerFormat.percent(BigDecimal("0.0099")))
        assertEquals("0.01%", ExplainerFormat.percent(BigDecimal("0.01")))
        assertEquals("0%", ExplainerFormat.percent(BigDecimal.ZERO))
    }

    @Test
    fun `label layout always draws today and drops labels that come within the gap`() {
        val labels = listOf(
            ExplainerLabelBox(centre = 20f, width = 40f, isToday = false),
            ExplainerLabelBox(centre = 100f, width = 40f, isToday = true),
            ExplainerLabelBox(centre = 130f, width = 40f, isToday = false),
            ExplainerLabelBox(centre = 300f, width = 40f, isToday = false),
            ExplainerLabelBox(centre = 395f, width = 40f, isToday = false),
        )
        val lefts = layoutExplainerLabels(labels, totalWidth = 400f, minGap = 12f)
        assertEquals(0f, lefts[0]) // clamped inside, 0..40 vs today 80..120: gap 40 >= 12
        assertEquals(80f, lefts[1])
        assertNull(lefts[2]) // overlaps Today
        assertEquals(280f, lefts[3])
        assertEquals(360f, lefts[4]) // clamped to the right edge
    }

    @Test
    fun `marker x fraction rises with the slider position`() {
        for (supply in listOf(30_000L, 1_250_000L, 12_000_000L)) {
            val calc = calculator(supply = supply * WHOLE_TOKEN)
            val fractions = (0..20).map { calc.chart(calc.snapshotAt(it / 20.0)).let { c -> c.xFraction(c.selectedTokens) } }
            assertTrue(fractions.zipWithNext().all { (a, b) -> b > a }, "supply=$supply: $fractions")
        }
    }

    private fun calcAtReserve(reserve: Long): MarketCapExplainerCalculator {
        val quarks = calculator().snapshotAtReserve(BigDecimal(reserve)).supplyQuarks
        return calculator(supply = quarks)
    }

    private fun stopsOf(calc: MarketCapExplainerCalculator) = calc.stops.map { it.toLong() }

    private val baseStops = listOf(5_000L, 100_000, 1_000_000, 10_000_000)

    @Test
    fun `small reserves keep the base stops`() {
        assertEquals(baseStops, stopsOf(calcAtReserve(22_700)))
        assertEquals(baseStops, stopsOf(calcAtReserve(171_000)))
        assertEquals(baseStops, stopsOf(calculator()))
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
        assertEquals(emptyList(), MarketCapExplainerCalculator.reserveStops(BigDecimal(6_000_000), BigDecimal(50_000_000))
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
