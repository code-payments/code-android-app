package com.flipcash.app.tokens.bondingcurve

import com.flipcash.libs.currency.math.Estimator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One labelled mark on the explainer slider's track.
 *
 * @property reserve dollars in the curve at this mark.
 * @property position 0..1 along the track, on a log(reserve) axis.
 * @property labelVisible false for a fixed tick that sits within [BondingCurveProjection.HIDE_LABEL_WITHIN]
 * of Today, so two labels do not collide.
 */
data class ExplainerTick(
    val reserve: BigDecimal,
    val position: Double,
    val isToday: Boolean,
    val labelVisible: Boolean,
)

/** The curve evaluated at one slider position. */
data class ExplainerSnapshot(
    val reserve: BigDecimal,
    val isToday: Boolean,
    /** Supply at [reserve], in whole tokens. */
    val supply: BigDecimal,
    /** [supply] in the token's quarks, the unit `Fiat.tokenBalance`'s `supplyOverride` takes. */
    val supplyQuarks: Long,
    /** Spot price per token at [supply], in dollars. */
    val price: BigDecimal,
    /** Fee-free sell value of the held tokens at [supply]; null when the held quarks are unknown. */
    val worth: Fiat?,
)

data class ExplainerChartPoint(val tokens: Double, val price: Double)

/**
 * The chart for one [ExplainerSnapshot]. x runs over [0, [xMax]] tokens and y over [[yMin], [yMax]]
 * dollars, both linear. The curve is solid up to [selectedTokens] and dashed beyond it.
 */
data class ExplainerChart(
    val points: List<ExplainerChartPoint>,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
    val selectedTokens: Double,
    val selectedPrice: Double,
    val todayTokens: Double,
    val todayPrice: Double,
) {
    /** [tokens] as a 0..1 fraction of the x-domain, clamped so nothing is ever positioned outside it. */
    fun xFraction(tokens: Double): Float =
        if (xMax <= 0.0) 0f else (tokens / xMax).coerceIn(0.0, 1.0).toFloat()
}

/** What the user owns today, independent of the slider. A null field is unknown. */
data class ExplainerOwnership(
    val tokensHeld: BigDecimal?,
    val price: BigDecimal,
    val shareOfCirculating: BigDecimal?,
    val shareOfMax: BigDecimal?,
)

/**
 * The math behind the "How Market Cap Works" screen, free of Android and Compose types.
 *
 * The slider's axis is the reserve: the dollars in the curve, `tokensToValue(0, supply)`. The supply
 * at a reserve is the curve's own inverse (`Estimator.supplyAtReserve`, which delegates to the shared
 * KMP engine), so nothing here approximates the curve in closed form.
 *
 * @param todaySupplyQuarks the token's current circulating supply, in quarks.
 * @param heldQuarks quarks the user holds; null when only a USD value is known (a balance restored
 * from the local database), which leaves worth and the share rows unknown.
 */
class BondingCurveProjection(
    private val token: Token,
    private val todaySupplyQuarks: Long,
    private val heldQuarks: Long?,
) {
    private val quarksPerToken: BigDecimal = BigDecimal.TEN.pow(token.decimals)

    val todayReserve: BigDecimal = Estimator
        .reserveAtSupply(todaySupplyQuarks.toBigDecimal().divide(quarksPerToken))
        .getOrThrow()

    /** The reserve at max supply: no stop is added beyond it, since the curve has no point there. */
    private val curveMaxReserve: BigDecimal? =
        Estimator.reserveAtSupply(BigDecimal(MAX_SUPPLY_TOKENS)).getOrNull()

    /** The fixed stops for this token's Today: [FIXED_RESERVES], extended by [reserveStops]. */
    val stops: List<BigDecimal> = reserveStops(todayReserve, curveMaxReserve)

    private val minReserve: BigDecimal = stops.first().min(todayReserve)
    private val maxReserve: BigDecimal = stops.last().max(todayReserve)
    private val logMin = ln(minReserve.toDouble())
    private val logSpan = ln(maxReserve.toDouble()) - logMin

    /** Fixed ticks plus Today, ordered along the track. */
    val ticks: List<ExplainerTick> = run {
        val today = ExplainerTick(todayReserve, positionOf(todayReserve), isToday = true, labelVisible = true)
        val fixed = stops.map { reserve ->
            val nearToday = abs(ln(reserve.toDouble()) - ln(todayReserve.toDouble())) <
                ln(1.0 + HIDE_LABEL_WITHIN)
            ExplainerTick(reserve, positionOf(reserve), isToday = false, labelVisible = !nearToday)
        }
        (fixed + today).sortedBy { it.position }
    }

    val todayPosition: Double get() = positionOf(todayReserve)

    /** 0..1 along the log(reserve) track. */
    fun positionOf(reserve: BigDecimal): Double {
        if (logSpan <= 0.0) return 0.0
        return ((ln(reserve.toDouble()) - logMin) / logSpan).coerceIn(0.0, 1.0)
    }

    fun reserveAt(position: Double): BigDecimal {
        val clamped = position.coerceIn(0.0, 1.0)
        return BigDecimal(exp(logMin + clamped * logSpan))
    }

    /** The slider's value for [position]; within [TODAY_MAGNET] of Today it is Today exactly. */
    fun snapshotAt(position: Double): ExplainerSnapshot =
        if (abs(position - todayPosition) < TODAY_MAGNET) today() else snapshotAtReserve(reserveAt(position))

    fun today(): ExplainerSnapshot = snapshot(
        reserve = todayReserve,
        supply = todaySupplyQuarks.toBigDecimal().divide(quarksPerToken),
        supplyQuarks = todaySupplyQuarks,
        isToday = true,
    )

    fun snapshotAtReserve(reserve: BigDecimal): ExplainerSnapshot {
        val supply = Estimator.supplyAtReserve(reserve).getOrThrow()
        val quarks = supply.multiply(quarksPerToken).setScale(0, RoundingMode.DOWN).longValueExact()
        return snapshot(reserve, supply, quarks, isToday = false)
    }

    private fun snapshot(
        reserve: BigDecimal,
        supply: BigDecimal,
        supplyQuarks: Long,
        isToday: Boolean,
    ): ExplainerSnapshot {
        val price = Estimator.currentPriceFor(supplyQuarks).getOrThrow()
        // Only supply that exists can be sold, so a holding larger than the supply at a low reserve
        // is valued as the whole supply.
        val worth = heldQuarks?.let { Fiat.tokenBalance(min(it, supplyQuarks), token, supplyOverride = supplyQuarks) }
        return ExplainerSnapshot(reserve, isToday, supply, supplyQuarks, price, worth)
    }

    // Spot prices on a fixed grid. The table's step is 100 tokens, so the grid is a lookup, not a curve fit.
    private val gridPrices: DoubleArray by lazy {
        DoubleArray((MAX_SUPPLY_TOKENS / CHART_STEP_TOKENS) + 1) { priceAt((it * CHART_STEP_TOKENS).toDouble()) }
    }

    private fun priceAt(tokens: Double): Double {
        val quarks = BigDecimal(tokens).multiply(quarksPerToken).setScale(0, RoundingMode.DOWN).longValueExact()
        return Estimator.currentPriceFor(quarks).getOrThrow().toDouble()
    }

    fun chart(snapshot: ExplainerSnapshot): ExplainerChart {
        val selected = snapshot.supply.toDouble()
        val today = todaySupplyQuarks.toBigDecimal().divide(quarksPerToken).toDouble()
        val xMax = domainMax(selected, today)
        val grid = (0..(xMax / CHART_STEP_TOKENS).toInt()).map {
            ExplainerChartPoint((it * CHART_STEP_TOKENS).toDouble(), gridPrices[it])
        }
        val extras = listOf(selected, xMax).map { ExplainerChartPoint(it, priceAt(it)) }
        val points = (grid + extras).sortedBy { it.tokens }.distinctBy { it.tokens }
        return ExplainerChart(
            points = points,
            xMax = xMax,
            yMin = CHART_MIN_PRICE,
            yMax = points.maxOf { it.price },
            selectedTokens = selected,
            selectedPrice = snapshot.price.toDouble(),
            todayTokens = today,
            todayPrice = today().price.toDouble(),
        )
    }

    fun ownership(): ExplainerOwnership {
        val held = heldQuarks?.toBigDecimal()?.divide(quarksPerToken)
        val circulating = todaySupplyQuarks.toBigDecimal().divide(quarksPerToken)
        return ExplainerOwnership(
            tokensHeld = held,
            price = Estimator.currentPriceFor(todaySupplyQuarks).getOrThrow(),
            shareOfCirculating = held?.takeIf { circulating.signum() > 0 }
                ?.multiply(HUNDRED)?.divide(circulating, MC),
            shareOfMax = held?.multiply(HUNDRED)?.divide(MAX_SUPPLY_TOKENS.toBigDecimal(), MC),
        )
    }

    companion object {
        /**
         * [FIXED_RESERVES] while the top stop is within twice Today's reserve, ten times the top stop
         * is appended, so Today always sits strictly left of the last stop. A stop above [curveMax]
         * (the reserve at max supply) is never added.
         */
        fun reserveStops(todayReserve: BigDecimal, curveMax: BigDecimal?): List<BigDecimal> {
            val stops = FIXED_RESERVES.toMutableList()
            val twiceToday = todayReserve.multiply(BigDecimal(2))
            while (stops.last() <= twiceToday) {
                val next = stops.last().multiply(BigDecimal.TEN)
                if (curveMax == null || next > curveMax) break
                stops += next
            }
            return stops
        }

        /**
         * The chart's x extent: both the selection and Today's dot must fit, so it is driven by the
         * larger of the two plus a tail. Sizing it from the selection alone put Today's dot far past
         * the right edge whenever the slider sat below Today.
         */
        fun domainMax(selectedTokens: Double, todayTokens: Double): Double {
            val rightmost = max(selectedTokens, todayTokens)
            return (rightmost + CHART_TAIL_TOKENS)
                .coerceAtMost(MAX_SUPPLY_TOKENS.toDouble() - 1.0)
                .coerceAtLeast(rightmost)
                .coerceAtLeast(1.0)
        }

        /** $5K, $100K, $1M, $10M. */
        val FIXED_RESERVES: List<BigDecimal> =
            listOf(5_000, 100_000, 1_000_000, 10_000_000).map { BigDecimal(it) }

        /** A fixed tick this close to Today (as a fraction) loses its label. */
        const val HIDE_LABEL_WITHIN = 0.10

        /** Slider positions this close to Today (fraction of the track) read as Today. */
        const val TODAY_MAGNET = 0.012

        const val MAX_SUPPLY_TOKENS = 21_000_000
        const val CHART_STEP_TOKENS = 25_000
        const val CHART_TAIL_TOKENS = 750_000.0
        const val CHART_MIN_PRICE = 0.01

        private val HUNDRED = BigDecimal(100)
        private val MC = MathContext(20)
    }
}
