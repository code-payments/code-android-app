package com.flipcash.app.tokens.internal.components.marketcap

import com.flipcash.app.tokens.data.MarketCapPoint
import com.flipcash.app.tokens.data.MarketTrend
import com.flipcash.app.tokens.data.Period
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.components.charts.ChartPoint
import java.util.Random
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal fun generateMarketCapData(
    currentMarketCap: Fiat,
    mintDate: Instant,
    period: Period,
    trend: MarketTrend = MarketTrend.Bullish
): List<MarketCapPoint> {
    return generateMarketCapData(
        trend = trend,
        mintDate = mintDate,
        period = period,
        endValue = currentMarketCap.quarks.toDouble(),
    )
}

internal fun generateMarketCapData(
    points: Int = 100,
    endValue: Double = 1_000_000.0,
    trend: MarketTrend = MarketTrend.Bullish,
    period: Period = Period.All,
    mintDate: Instant = Clock.System.now() - 365.days,
): List<MarketCapPoint> {
    val random = Random(System.currentTimeMillis())

    val now = Clock.System.now()
    val isAllTime = period == Period.All

    val requestedDuration = when (period) {
        Period.Day -> 1.days
        Period.Week -> 7.days
        Period.Month -> 30.days
        Period.Year -> 365.days
        Period.All -> now - mintDate
    }

    // Don't go before mint date
    val effectiveDuration = minOf(requestedDuration, now - mintDate)

    if (effectiveDuration <= Duration.ZERO) {
        return listOf(ChartPoint(x = now.toEpochMilliseconds(), y = endValue))
    }

    val changePercent = when {
        isAllTime -> 0.95..0.99
        effectiveDuration <= 1.days -> 0.02..0.08
        effectiveDuration <= 7.days -> 0.05..0.20
        effectiveDuration <= 30.days -> 0.15..0.50
        else -> 0.30..1.0
    }.let { range ->
        range.start + random.nextDouble() * (range.endInclusive - range.start)
    }

    val (startValue, baseVolatility) = when {
        isAllTime -> 0.0 to 0.05
        else -> when (trend) {
            MarketTrend.Bullish -> endValue / (1 + changePercent) to 0.33
            MarketTrend.Bearish -> endValue * (1 + changePercent) to 0.16
            MarketTrend.Sideways -> endValue * (1 - changePercent * 0.1) to 0.04
            MarketTrend.Volatile -> endValue / (1 + changePercent * 0.5) to 0.7
        }
    }

    var momentum = 0.0

    val endTime = now.toEpochMilliseconds()
    val startTime = endTime - effectiveDuration.inWholeMilliseconds
    val timeStep = effectiveDuration.inWholeMilliseconds / (points - 1).coerceAtLeast(1)

    return List(points) { i ->
        val progress = i.toDouble() / (points - 1).coerceAtLeast(1)

        if (isAllTime && i == 0) {
            return@List MarketCapPoint(
                x = startTime,
                y = 0.0
            )
        }

        val easedProgress = when {
            isAllTime -> 1 - (1 - progress).pow(3.0)
            else -> when (trend) {
                MarketTrend.Bullish -> 1 - (1 - progress).pow(2.5)
                MarketTrend.Bearish -> progress.pow(2.5)
                MarketTrend.Sideways -> progress
                MarketTrend.Volatile -> progress
            }
        }

        val target = startValue + (endValue - startValue) * easedProgress
        val noiseScale = target.coerceAtLeast(1.0)
        val noise = random.nextGaussian() * noiseScale * baseVolatility
        momentum = 0.7 * momentum + 0.3 * noise

        val value = if (i == points - 1) {
            endValue
        } else {
            (target + momentum).coerceAtLeast(0.0)
        }

        MarketCapPoint(
            x = startTime + (timeStep * i),
            y = value
        )
    }
}

internal fun generateMarketCapData(
    endValue: Double = 1_000_000.0,
    trend: MarketTrend = MarketTrend.Bullish,
    duration: Duration = 30.days,
    mintDate: Instant = Clock.System.now() - 365.days,
): List<MarketCapPoint> {
    val random = Random(System.currentTimeMillis())

    val now = Clock.System.now()
    val isAllTime = duration == Duration.INFINITE

    val effectiveDuration = if (isAllTime) {
        now - mintDate
    } else {
        minOf(duration, now - mintDate)
    }

    if (effectiveDuration <= Duration.ZERO) {
        return listOf(MarketCapPoint(x = now.toEpochMilliseconds(), y = endValue))
    }

    val interval = when {
        effectiveDuration > 30.days -> 6.hours
        effectiveDuration > 7.days -> 1.hours
        effectiveDuration > 1.days -> 30.minutes
        else -> 15.minutes
    }

    val points = (effectiveDuration.inWholeMinutes / interval.inWholeMinutes).toInt().coerceAtLeast(2)

    val changePercent = when {
        isAllTime -> 0.95..0.99
        effectiveDuration <= 1.days -> 0.02..0.08
        effectiveDuration <= 7.days -> 0.05..0.20
        effectiveDuration <= 30.days -> 0.15..0.50
        else -> 0.30..1.0
    }.let { range ->
        range.start + random.nextDouble() * (range.endInclusive - range.start)
    }

    val (startValue, baseVolatility) = when {
        isAllTime -> 0.0 to 0.05
        else -> when (trend) {
            MarketTrend.Bullish -> endValue / (1 + changePercent) to 0.33
            MarketTrend.Bearish -> endValue * (1 + changePercent) to 0.16
            MarketTrend.Sideways -> endValue * (1 - changePercent * 0.1) to 0.04
            MarketTrend.Volatile -> endValue / (1 + changePercent * 0.5) to 0.7
        }
    }

    var momentum = 0.0

    val endTime = now.toEpochMilliseconds()
    val startTime = maxOf(
        mintDate.toEpochMilliseconds(),
        endTime - effectiveDuration.inWholeMilliseconds
    )

    return List(points) { i ->
        val progress = i.toDouble() / (points - 1).coerceAtLeast(1)

        // First point is always 0 for all-time
        if (isAllTime && i == 0) {
            return@List MarketCapPoint(
                x = startTime,
                y = 0.0
            )
        }

        val easedProgress = when {
            isAllTime -> 1 - (1 - progress).pow(3.0)
            else -> when (trend) {
                MarketTrend.Bullish -> 1 - (1 - progress).pow(2.5)
                MarketTrend.Bearish -> progress.pow(2.5)
                MarketTrend.Sideways -> progress
                MarketTrend.Volatile -> progress
            }
        }

        val target = startValue + (endValue - startValue) * easedProgress
        val noiseScale = target.coerceAtLeast(1.0)
        val noise = random.nextGaussian() * noiseScale * baseVolatility
        momentum = 0.7 * momentum + 0.3 * noise

        val value = if (i == points - 1) {
            endValue
        } else {
            (target + momentum).coerceAtLeast(0.0)
        }

        MarketCapPoint(
            x = startTime + (interval.inWholeMilliseconds * i),
            y = value
        )
    }
}