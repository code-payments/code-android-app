package com.flipcash.app.tokens.data

import com.getcode.ui.components.charts.ChartPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AggregationTypeTest {

    private fun point(x: Long, y: Double) = ChartPoint(x = x, y = y)

    // --- Bucketed: Last ---

    @Test
    fun lastReturnsHighestTimestampValue() {
        val points = listOf(point(1, 10.0), point(3, 30.0), point(2, 20.0))
        assertEquals(30.0, AggregationType.Last.aggregate(points))
    }

    @Test
    fun lastEmptyReturnsNull() {
        assertNull(AggregationType.Last.aggregate(emptyList()))
    }

    // --- Bucketed: First ---

    @Test
    fun firstReturnsLowestTimestampValue() {
        val points = listOf(point(3, 30.0), point(1, 10.0), point(2, 20.0))
        assertEquals(10.0, AggregationType.First.aggregate(points))
    }

    @Test
    fun firstEmptyReturnsNull() {
        assertNull(AggregationType.First.aggregate(emptyList()))
    }

    // --- Bucketed: Average ---

    @Test
    fun averageComputesMean() {
        val points = listOf(point(1, 10.0), point(2, 20.0), point(3, 30.0))
        assertEquals(20.0, AggregationType.Average.aggregate(points))
    }

    @Test
    fun averageSinglePoint() {
        assertEquals(42.0, AggregationType.Average.aggregate(listOf(point(1, 42.0))))
    }

    @Test
    fun averageEmptyReturnsNaN() {
        // List.average() returns NaN for empty lists
        val result = AggregationType.Average.aggregate(emptyList())
        assertTrue(result != null && result.isNaN())
    }

    // --- Bucketed: Max ---

    @Test
    fun maxReturnsHighestValue() {
        val points = listOf(point(1, 10.0), point(2, 50.0), point(3, 30.0))
        assertEquals(50.0, AggregationType.Max.aggregate(points))
    }

    @Test
    fun maxEmptyReturnsNull() {
        assertNull(AggregationType.Max.aggregate(emptyList()))
    }

    // --- Bucketed: Min ---

    @Test
    fun minReturnsLowestValue() {
        val points = listOf(point(1, 10.0), point(2, 5.0), point(3, 30.0))
        assertEquals(5.0, AggregationType.Min.aggregate(points))
    }

    @Test
    fun minEmptyReturnsNull() {
        assertNull(AggregationType.Min.aggregate(emptyList()))
    }

    // --- LTTB Downsample ---

    @Test
    fun lttbFewPointsReturnsTargetCount() {
        val points = listOf(point(100, 1.0), point(200, 2.0), point(300, 3.0))
        val result = AggregationType.LargestTriangleThreeBuckets.downsample(
            startTime = 0,
            now = 1000,
            points = points,
            targetPoints = 10,
            currentValue = 5.0,
        )
        assertEquals(10, result.size)
    }

    @Test
    fun lttbLastBucketIsCurrentValue() {
        val points = listOf(point(100, 1.0), point(500, 3.0))
        val result = AggregationType.LargestTriangleThreeBuckets.downsample(
            startTime = 0,
            now = 1000,
            points = points,
            targetPoints = 5,
            currentValue = 99.0,
        )
        assertEquals(99.0, result.last().y)
    }

    @Test
    fun lttbTargetLessThan3ReturnsTwoPoints() {
        val points = listOf(point(100, 1.0), point(200, 2.0), point(300, 3.0))
        val result = AggregationType.LargestTriangleThreeBuckets.downsample(
            startTime = 0,
            now = 1000,
            points = points,
            targetPoints = 2,
            currentValue = 5.0,
        )
        assertEquals(2, result.size)
    }

    @Test
    fun lttbTimestampsAreMonotonicallyIncreasing() {
        val points = (1..50).map { point(it.toLong() * 10, it.toDouble()) }
        val result = AggregationType.LargestTriangleThreeBuckets.downsample(
            startTime = 0,
            now = 1000,
            points = points,
            targetPoints = 20,
            currentValue = 100.0,
        )
        for (i in 1 until result.size) {
            assertTrue(result[i].x > result[i - 1].x, "Timestamps must be monotonically increasing")
        }
    }

    @Test
    fun lttbSignificantGapProducesZeroFill() {
        // Data starts at 600 out of 0..1000 — 60% gap, well above the 5% threshold
        val points = listOf(point(600, 10.0), point(800, 20.0))
        val result = AggregationType.LargestTriangleThreeBuckets.downsample(
            startTime = 0,
            now = 1000,
            points = points,
            targetPoints = 10,
            currentValue = 25.0,
        )
        // Early buckets (before the data starts at 600) should be zero-filled
        val earlyBuckets = result.filter { it.x < 600 }
        assertTrue(earlyBuckets.isNotEmpty())
        earlyBuckets.forEach { assertEquals(0.0, it.y) }
    }

    @Test
    fun lttbSmallGapNoZeroFill() {
        // Data starts at 20 out of 0..1000 — 2% gap, below the 5% threshold
        val points = listOf(point(20, 10.0), point(500, 20.0), point(900, 30.0))
        val result = AggregationType.LargestTriangleThreeBuckets.downsample(
            startTime = 0,
            now = 1000,
            points = points,
            targetPoints = 10,
            currentValue = 35.0,
        )
        // No zero-filled buckets since the gap is small
        val nonLastBuckets = result.dropLast(1)
        nonLastBuckets.forEach { assertTrue(it.y > 0.0, "No zero-fill expected for small gap") }
    }

    @Test
    fun lttbManyPointsDownsampled() {
        val points = (1..500).map { point(it.toLong() * 2, it.toDouble()) }
        val result = AggregationType.LargestTriangleThreeBuckets.downsample(
            startTime = 0,
            now = 1001,
            points = points,
            targetPoints = 50,
            currentValue = 999.0,
        )
        assertEquals(50, result.size)
    }
}
