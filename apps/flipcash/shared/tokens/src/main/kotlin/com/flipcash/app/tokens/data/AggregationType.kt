package com.flipcash.app.tokens.data

import kotlin.math.abs

sealed interface AggregationType {
    sealed interface Downsample : AggregationType {
        fun downsample(
            startTime: Long,
            now: Long,
            points: List<MarketCapPoint>,
            targetPoints: Int,
            currentValue: Double,
        ): List<MarketCapPoint>
    }

    sealed interface Bucketed : AggregationType {
        fun aggregate(points: List<MarketCapPoint>): Double?
    }

    data object Last : Bucketed {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.maxByOrNull { it.x }?.y
        }
    }

    data object First : Bucketed {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.minByOrNull { it.x }?.y
        }
    }

    data object Average : Bucketed {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.map { it.y }.average()
        }
    }

    data object Max : Bucketed {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.maxOfOrNull { it.y }
        }
    }

    data object Min : Bucketed {
        override fun aggregate(points: List<MarketCapPoint>): Double? {
            return points.minOfOrNull { it.y }
        }
    }

    data object LargestTriangleThreeBuckets : Downsample {
        override fun downsample(
            startTime: Long,
            now: Long,
            points: List<MarketCapPoint>,
            targetPoints: Int,
            currentValue: Double,
        ): List<MarketCapPoint> {
            val withCurrent = points + MarketCapPoint(x = now, y = currentValue)
            val sorted = withCurrent.sortedBy { it.x }

            // Find the first real data point (non-zero timestamp)
            // Instead of checking for x == 0, check if there's a gap at the start
            val firstRealPoint = sorted.firstOrNull { it.y > 0.0 }

            val duration = now - startTime
            val gapThreshold = duration * 0.05 // 5% of the period

            // Only treat the start as a zero-region when the gap before
            // the first real data point is significant (> 5 % of the period).
            // Small gaps (data starting a few minutes/hours into the window)
            // should NOT produce a zero spike at the left edge of the chart.
            val hasSignificantGap = firstRealPoint != null &&
                firstRealPoint.x > startTime &&
                (firstRealPoint.x - startTime) > gapThreshold

            val effective = if (hasSignificantGap) {
                val zeroAnchors = listOf(
                    MarketCapPoint(x = startTime, y = 0.0),
                    MarketCapPoint(x = firstRealPoint!!.x - 1, y = 0.0),
                )
                zeroAnchors + sorted.filter { it.x > 0 }
            } else {
                sorted
            }

            // Select visually significant points using LTTB
            val selected = when {
                effective.size <= targetPoints -> effective
                targetPoints < 3 -> listOfNotNull(
                    effective.firstOrNull(),
                    effective.lastOrNull()
                ).distinct()

                else -> selectPoints(effective, targetPoints)
            }

            // Interpolate to evenly-spaced timestamps
            val intervalMs = (duration / targetPoints).coerceAtLeast(1)
            val firstRealTimestamp = firstRealPoint?.x ?: startTime

            return (0 until targetPoints).map { bucket ->
                val timestamp = startTime + (bucket * intervalMs) + (intervalMs / 2)
                val y = when {
                    bucket == targetPoints - 1 -> currentValue
                    // Zero-fill only when there is a genuine gap at the start
                    hasSignificantGap && timestamp < firstRealTimestamp -> 0.0
                    else -> interpolateAt(selected, timestamp)
                }
                MarketCapPoint(x = timestamp, y = y)
            }
        }

        private fun selectPoints(sorted: List<MarketCapPoint>, targetPoints: Int): List<MarketCapPoint> {
            val result = mutableListOf<MarketCapPoint>()
            result.add(sorted.first())

            val bucketSize = (sorted.size - 2).toDouble() / (targetPoints - 2)
            var prevSelectedIndex = 0

            for (i in 0 until targetPoints - 2) {
                val bucketStart = ((i + 1) * bucketSize).toInt() + 1
                val bucketEnd = (((i + 2) * bucketSize).toInt() + 1).coerceAtMost(sorted.size - 1)

                val nextBucketEnd = (((i + 3) * bucketSize).toInt() + 1).coerceAtMost(sorted.size)

                val (avgX, avgY) = if (bucketEnd < nextBucketEnd) {
                    val nextBucket = sorted.subList(bucketEnd, nextBucketEnd)
                    nextBucket.map { it.x }.average() to nextBucket.map { it.y }.average()
                } else {
                    sorted.last().x.toDouble() to sorted.last().y
                }

                val prev = sorted[prevSelectedIndex]
                var maxArea = -1.0
                var selectedIndex = bucketStart

                for (j in bucketStart until bucketEnd) {
                    val current = sorted[j]
                    val area = triangleArea(
                        prev.x.toDouble(), prev.y,
                        current.x.toDouble(), current.y,
                        avgX, avgY
                    )
                    if (area > maxArea) {
                        maxArea = area
                        selectedIndex = j
                    }
                }

                result.add(sorted[selectedIndex])
                prevSelectedIndex = selectedIndex
            }

            result.add(sorted.last())
            return result
        }

        private fun interpolateAt(points: List<MarketCapPoint>, timestamp: Long): Double {
            if (points.isEmpty()) return 0.0
            if (timestamp <= points.first().x) return points.first().y
            if (timestamp >= points.last().x) return points.last().y

            // Binary search for surrounding points
            val insertionPoint = points.binarySearchBy(timestamp) { it.x }

            return if (insertionPoint >= 0) {
                points[insertionPoint].y
            } else {
                val rightIndex = -(insertionPoint + 1)
                val leftIndex = rightIndex - 1

                val left = points[leftIndex]
                val right = points[rightIndex]

                // Linear interpolation
                val t = (timestamp - left.x).toDouble() / (right.x - left.x)
                left.y + t * (right.y - left.y)
            }
        }

        private fun triangleArea(
            x1: Double, y1: Double,
            x2: Double, y2: Double,
            x3: Double, y3: Double
        ): Double = abs((x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2)) / 2.0)
    }
}