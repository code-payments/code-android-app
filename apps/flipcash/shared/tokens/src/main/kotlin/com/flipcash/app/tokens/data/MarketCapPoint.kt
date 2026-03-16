package com.flipcash.app.tokens.data

import com.getcode.ui.components.charts.ChartPoint
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

typealias MarketCapPoint = ChartPoint<Long, Double>

fun List<MarketCapPoint>.collapse(
    period: Period,
    targetPoints: Int = 100,
    currentValue: Double,
    aggregation: AggregationType = AggregationType.LargestTriangleThreeBuckets,
): List<MarketCapPoint> {
    if (isEmpty()) return emptyList()
    val now = Clock.System.now().toEpochMilliseconds()

    val startTime = when (period) {
        Period.Day -> now - 1.days.inWholeMilliseconds
        Period.Week -> now - 7.days.inWholeMilliseconds
        Period.Month -> now - 30.days.inWholeMilliseconds
        Period.Year -> now - 365.days.inWholeMilliseconds
        Period.All -> minOfOrNull { it.x } ?: now
    }

    val filtered = filter { it.x >= startTime }
    if (filtered.isEmpty()) return emptyList()

    val duration = now - startTime
    val intervalMs = (duration / targetPoints).coerceAtLeast(1)

    return when (aggregation) {
        is AggregationType.Bucketed -> {
            val bucketedData = filtered
                .groupBy { (it.x - startTime) / intervalMs }
                .mapNotNull { (bucket, points) ->
                    val value = aggregation.aggregate(points) ?: return@mapNotNull null
                    bucket to value
                }
                .toMap()

            var lastValue = 0.0
            (0 until targetPoints).mapIndexed { index, bucket ->
                val timestamp = startTime + (bucket * intervalMs) + (intervalMs / 2)
                val value = bucketedData[bucket.toLong()]
                if (value != null) lastValue = value
                val y = if (index == targetPoints - 1) currentValue else lastValue
                MarketCapPoint(x = timestamp, y = y)
            }
        }

        is AggregationType.Downsample -> {
            aggregation.downsample(
                startTime = startTime,
                now = now,
                points = filtered,
                targetPoints = targetPoints,
                currentValue = currentValue
            )
        }
    }
}