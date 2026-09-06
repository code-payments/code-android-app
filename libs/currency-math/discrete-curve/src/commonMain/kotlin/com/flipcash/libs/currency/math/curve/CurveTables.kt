package com.flipcash.libs.currency.math.curve

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Pre-computed lookup tables for the discrete bonding curve, held as raw scaled-integer values
 * (18-decimal fixed point, matching the on-chain Rust tables bit-for-bit).
 *
 * Loading the `.bin` resource bytes stays platform-specific (Android reads them from
 * `context.assets`, iOS from `Bundle.module`) -- this object only turns already-read bytes into
 * usable tables, so it has no platform dependency of its own.
 */
internal object DiscreteCurveTables {
    private var pricing: List<BigInteger>? = null
    private var cumulative: List<BigInteger>? = null

    val pricingTable: List<BigInteger>
        get() = pricing ?: error("DiscreteCurveTables not initialized")

    val cumulativeTable: List<BigInteger>
        get() = cumulative ?: error("DiscreteCurveTables not initialized")

    val isInitialized: Boolean
        get() = pricing != null && cumulative != null

    fun initialize(pricingTableBytes: ByteArray, cumulativeTableBytes: ByteArray) {
        if (pricing == null) pricing = parseTable(pricingTableBytes)
        if (cumulative == null) cumulative = parseTable(cumulativeTableBytes)
    }

    /** Each entry is 16 little-endian bytes: 8 bytes low u64, then 8 bytes high u64. */
    private fun parseTable(bytes: ByteArray): List<BigInteger> {
        val entrySize = 16
        val count = bytes.size / entrySize
        return List(count) { i ->
            val offset = i * entrySize
            var low = BigInteger.ZERO
            for (b in 7 downTo 0) {
                low = (low shl 8) or BigInteger.fromInt(bytes[offset + b].toInt() and 0xFF)
            }
            var high = BigInteger.ZERO
            for (b in 7 downTo 0) {
                high = (high shl 8) or BigInteger.fromInt(bytes[offset + 8 + b].toInt() and 0xFF)
            }
            (high shl 64) or low
        }
    }
}
