package com.flipcash.libs.currency.math.curve

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * GATE: decoding a table entry on access must produce exactly what eagerly parsing the whole table
 * produced. These are the u128 step prices the on-chain curve is priced against, so a single wrong
 * entry misprices a trade -- the decode is compared entry-for-entry against the original algorithm
 * over both real tables, not sampled.
 */
class CurveTableDecodeTest {

    @OptIn(ExperimentalEncodingApi::class)
    private fun readTestResourceBytes(name: String): ByteArray =
        Base64.decode(readTestResource("$name.bin.b64").trim())

    /**
     * The eager parse this module used before, kept here as the reference implementation: shift a
     * byte at a time into an immutable [BigInteger], low u64 then high u64, both little-endian.
     */
    private fun referenceDecode(bytes: ByteArray, index: Int): BigInteger {
        val offset = index * 16
        var low = BigInteger.ZERO
        for (b in 7 downTo 0) {
            low = (low shl 8) or BigInteger.fromInt(bytes[offset + b].toInt() and 0xFF)
        }
        var high = BigInteger.ZERO
        for (b in 7 downTo 0) {
            high = (high shl 8) or BigInteger.fromInt(bytes[offset + 8 + b].toInt() and 0xFF)
        }
        return (high shl 64) or low
    }

    private fun assertDecodesLikeReference(name: String, table: List<BigInteger>, bytes: ByteArray) {
        assertEquals(bytes.size / 16, table.size, "$name: entry count")
        assertEquals(DiscreteCurveEngine.TABLE_SIZE, table.size, "$name: expected table size")
        for (index in table.indices) {
            val expected = referenceDecode(bytes, index)
            if (table[index] != expected) {
                // Only build the message on failure -- formatting 210,001 of these is not free.
                assertEquals(expected, table[index], "$name: entry $index")
            }
        }
    }

    @Test
    fun every_table_entry_decodes_to_the_eagerly_parsed_value() {
        val pricingBytes = readTestResourceBytes("discrete_pricing_table")
        val cumulativeBytes = readTestResourceBytes("discrete_cumulative_table")
        if (!DiscreteCurveTables.isInitialized) {
            DiscreteCurveEngine.initialize(pricingBytes, cumulativeBytes)
        }

        assertDecodesLikeReference("pricing", DiscreteCurveTables.pricingTable, pricingBytes)
        assertDecodesLikeReference("cumulative", DiscreteCurveTables.cumulativeTable, cumulativeBytes)
    }
}
