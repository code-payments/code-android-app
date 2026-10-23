package com.getcode.opencode.model.financial

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class HistoricalMintDataTest {

    @Test
    fun `stores snapshot timestamp and market cap`() {
        val timestamp = Instant.parse("2024-06-15T12:00:00Z")
        val data = HistoricalMintData(
            snapshotAt = timestamp,
            marketCap = 1_500_000.0
        )

        assertEquals(timestamp, data.snapshotAt)
        assertEquals(1_500_000.0, data.marketCap, 0.01)
    }

    @Test
    fun `zero market cap`() {
        val data = HistoricalMintData(
            snapshotAt = Instant.parse("2024-01-01T00:00:00Z"),
            marketCap = 0.0
        )

        assertEquals(0.0, data.marketCap, 0.001)
    }

    @Test
    fun `very large market cap`() {
        val data = HistoricalMintData(
            snapshotAt = Instant.parse("2024-12-31T23:59:59Z"),
            marketCap = 1_000_000_000_000.0
        )

        assertEquals(1_000_000_000_000.0, data.marketCap, 0.01)
    }

    @Test
    fun `two instances with same data are equal`() {
        val timestamp = Instant.parse("2024-06-15T12:00:00Z")
        val d1 = HistoricalMintData(snapshotAt = timestamp, marketCap = 100.0)
        val d2 = HistoricalMintData(snapshotAt = timestamp, marketCap = 100.0)

        assertEquals(d1, d2)
        assertEquals(d1.hashCode(), d2.hashCode())
    }

    @Test
    fun `two instances with different timestamps are not equal`() {
        val d1 = HistoricalMintData(
            snapshotAt = Instant.parse("2024-01-01T00:00:00Z"),
            marketCap = 100.0
        )
        val d2 = HistoricalMintData(
            snapshotAt = Instant.parse("2024-06-01T00:00:00Z"),
            marketCap = 100.0
        )

        assertNotEquals(d1, d2)
    }

    @Test
    fun `two instances with different market caps are not equal`() {
        val timestamp = Instant.parse("2024-06-15T12:00:00Z")
        val d1 = HistoricalMintData(snapshotAt = timestamp, marketCap = 100.0)
        val d2 = HistoricalMintData(snapshotAt = timestamp, marketCap = 200.0)

        assertNotEquals(d1, d2)
    }

    @Test
    fun `copy changes only specified field`() {
        val original = HistoricalMintData(
            snapshotAt = Instant.parse("2024-06-15T12:00:00Z"),
            marketCap = 500.0
        )

        val modified = original.copy(marketCap = 750.0)

        assertEquals(original.snapshotAt, modified.snapshotAt)
        assertEquals(750.0, modified.marketCap, 0.01)
    }

    @Test
    fun `can represent data points in chronological order`() {
        val points = listOf(
            HistoricalMintData(Instant.parse("2024-01-01T00:00:00Z"), 100.0),
            HistoricalMintData(Instant.parse("2024-02-01T00:00:00Z"), 150.0),
            HistoricalMintData(Instant.parse("2024-03-01T00:00:00Z"), 200.0),
        )

        val sorted = points.sortedBy { it.snapshotAt }
        assertEquals(100.0, sorted.first().marketCap, 0.01)
        assertEquals(200.0, sorted.last().marketCap, 0.01)
        // Verify chronological ordering
        for (i in 0 until sorted.size - 1) {
            assertTrue(sorted[i].snapshotAt < sorted[i + 1].snapshotAt)
        }
    }
}
