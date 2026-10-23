package com.getcode.opencode.model.financial

import kotlin.time.Instant

data class HistoricalMintData(
    /**
     * Timestamp for this data point
     * */
    val snapshotAt: Instant,
    /**
     * Market capitalization at this point in time
     */
    val marketCap: Double,
)
