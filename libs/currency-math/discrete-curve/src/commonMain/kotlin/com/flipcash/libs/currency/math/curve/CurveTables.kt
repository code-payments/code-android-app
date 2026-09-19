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
        if (pricing == null) pricing = RawU128Table(pricingTableBytes)
        if (cumulative == null) cumulative = RawU128Table(cumulativeTableBytes)
    }
}

/**
 * A curve table read straight out of its `.bin` bytes, decoding an entry only when one is asked for.
 *
 * Each table is 3.36MB -- 210,001 entries of 16 little-endian bytes (low u64, then high u64). The
 * previous version decoded all of them up front into a `List<BigInteger>`, which cost roughly 50
 * allocations per entry because [BigInteger] is immutable and the decode shifted a byte at a time:
 * ~21 million short-lived objects across the two tables on every cold start, plus 420,002 boxed
 * entries retained for the life of the process. That ran on `Dispatchers.IO` from an
 * `androidx.startup` initializer, so it did not block the first frame, but it did saturate the
 * shared coroutine pool that the login path's key derivation runs on.
 *
 * Decoding on access costs 16 byte loads and three [BigInteger] operations. The engine reaches
 * these tables through binary searches that touch O(log n) entries per query -- about 18 of the
 * 210,001 -- so the work this defers is work that was almost entirely never needed.
 */
private class RawU128Table(private val bytes: ByteArray) : AbstractList<BigInteger>() {

    override val size: Int = bytes.size / ENTRY_SIZE

    override fun get(index: Int): BigInteger {
        if (index !in 0 until size) throw IndexOutOfBoundsException("index: $index, size: $size")
        val offset = index * ENTRY_SIZE
        var low = 0L
        for (b in 7 downTo 0) low = (low shl 8) or (bytes[offset + b].toLong() and 0xFF)
        var high = 0L
        for (b in 7 downTo 0) high = (high shl 8) or (bytes[offset + 8 + b].toLong() and 0xFF)
        return (BigInteger.fromULong(high.toULong()) shl 64) or BigInteger.fromULong(low.toULong())
    }

    private companion object {
        /** Each entry is 16 little-endian bytes: 8 bytes low u64, then 8 bytes high u64. */
        const val ENTRY_SIZE = 16
    }
}
