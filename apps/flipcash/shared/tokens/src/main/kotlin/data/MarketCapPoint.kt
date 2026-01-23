package com.flipcash.app.tokens.data

import com.getcode.ui.components.charts.ChartPoint
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

typealias MarketCapPoint = ChartPoint<Long, Double>

fun List<MarketCapPoint>.collapse(
    period: Period,
    targetPoints: Int = 100,
    aggregation: AggregationType = AggregationType.Last,
): List<MarketCapPoint> {
    if (isEmpty()) return emptyList()

    val now = Clock.System.now().toEpochMilliseconds()

    val startTime = when (period) {
        Period.Day -> now - 1.days.inWholeMilliseconds
        Period.Week -> now - 7.days.inWholeMilliseconds
        Period.Month -> now - 30.days.inWholeMilliseconds
        Period.Year -> now - 365.days.inWholeMilliseconds
        Period.All -> firstOrNull()?.x ?: now
    }

    val filtered = filter { it.x >= startTime }
    if (filtered.isEmpty()) return emptyList()

    val duration = now - startTime
    val intervalMs = (duration / targetPoints).coerceAtLeast(1)

    return filtered
        .groupBy { (it.x - startTime) / intervalMs }
        .mapNotNull { (bucket, points) ->
            val timestamp = startTime + (bucket * intervalMs) + (intervalMs / 2)
            val value = when (aggregation) {
                AggregationType.Last -> points.maxByOrNull { it.x }?.y
                AggregationType.First -> points.minByOrNull { it.x }?.y
                AggregationType.Average -> points.map { it.y }.average()
                AggregationType.Max -> points.maxOfOrNull { it.y }
                AggregationType.Min -> points.minOfOrNull { it.y }
            } ?: return@mapNotNull null

            ChartPoint(x = timestamp, y = value)
        }
        .sortedBy { it.x }
        .let { points ->
            // Ensure consistent point count for smooth morphing
            if (points.size == targetPoints) {
                points
            } else {
                points.interpolateToSize(targetPoints)
            }
        }
}

private fun List<MarketCapPoint>.interpolateToSize(targetSize: Int): List<MarketCapPoint> {
    if (size == targetSize || isEmpty()) return this

    val result = mutableListOf<MarketCapPoint>()
    val step = (size - 1).toFloat() / (targetSize - 1).coerceAtLeast(1)

    repeat(targetSize) { i ->
        val position = i * step
        val lowerIndex = position.toInt().coerceIn(0, size - 1)
        val upperIndex = (lowerIndex + 1).coerceAtMost(size - 1)
        val fraction = position - lowerIndex

        val lower = this[lowerIndex]
        val upper = this[upperIndex]

        result.add(
            MarketCapPoint(
                x = (lower.x + (upper.x - lower.x) * fraction).toLong(),
                y = (lower.y + (upper.y - lower.y) * fraction),
            )
        )
    }

    return result
}