package com.getcode.wire.canonical

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * [canonicalize] over hand-written bytes, so no generated messages are needed. The OCP and Flipcash
 * modules check real messages against protobuf-java's bytes.
 */
class CanonicalizeTest {

    private val s = CanonicalField.Scalar

    @Test
    fun fieldsAreWrittenInNumberOrder() {
        val schema = CanonicalSchema(mapOf("t" to mapOf(1 to s, 2 to s, 3 to s)))
        // Wire writes a oneof member (here field 1) after the regular fields 2 and 3.
        val wire = bytes(0x10, 2, 0x18, 3, 0x08, 1)
        assertContentEquals(bytes(0x08, 1, 0x10, 2, 0x18, 3), canonicalize(wire, "t", schema))
    }

    @Test
    fun repeatedFieldsKeepTheirOrder() {
        val schema = CanonicalSchema(mapOf("t" to mapOf(1 to s, 2 to s)))
        val input = bytes(0x10, 9, 0x08, 3, 0x08, 1, 0x08, 2)
        assertContentEquals(bytes(0x08, 3, 0x08, 1, 0x08, 2, 0x10, 9), canonicalize(input, "t", schema))
    }

    @Test
    fun nestedMessagesAreRewritten() {
        val schema = CanonicalSchema(
            mapOf(
                "outer" to mapOf(1 to CanonicalField.Message("inner"), 2 to s),
                "inner" to mapOf(1 to s, 2 to s),
            ),
        )
        // outer { 2: 7, 1: inner { 2: 5, 1: 4 } }
        val input = bytes(0x10, 7, 0x0a, 4, 0x10, 5, 0x08, 4)
        assertContentEquals(bytes(0x0a, 4, 0x08, 4, 0x10, 5, 0x10, 7), canonicalize(input, "outer", schema))
    }

    @Test
    fun unknownFieldsGoLast() {
        val schema = CanonicalSchema(mapOf("t" to mapOf(1 to s, 3 to s)))
        val input = bytes(0x18, 1, 0x10, 2, 0x08, 3)
        assertContentEquals(bytes(0x08, 3, 0x18, 1, 0x10, 2), canonicalize(input, "t", schema))
    }

    @Test
    fun stringKeyedMapEntriesAreSortedByKeyBytes() {
        val schema = CanonicalSchema(mapOf("t" to mapOf(1 to CanonicalField.MapOf(MapKey.Bytes, null))))
        val b = entry(bytes(0x0a, 1, 'b'.code, 0x10, 1))
        val a = entry(bytes(0x0a, 1, 'a'.code, 0x10, 2))
        val ab = entry(bytes(0x0a, 2, 'a'.code, 'b'.code, 0x10, 3))
        assertContentEquals(a + ab + b, canonicalize(b + ab + a, "t", schema))
    }

    @Test
    fun mapEntryKeyIsWrittenBeforeValue() {
        val schema = CanonicalSchema(mapOf("t" to mapOf(1 to CanonicalField.MapOf(MapKey.Unsigned, null))))
        assertContentEquals(entry(bytes(0x08, 1, 0x10, 2)), canonicalize(entry(bytes(0x10, 2, 0x08, 1)), "t", schema))
    }

    @Test
    fun signedKeysSortNegativesFirst() {
        val schema = CanonicalSchema(mapOf("t" to mapOf(1 to CanonicalField.MapOf(MapKey.Signed, null))))
        val one = entry(bytes(0x08, 1))
        val minusOne = entry(bytes(0x08, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x01))
        assertContentEquals(minusOne + one, canonicalize(one + minusOne, "t", schema))
    }

    @Test
    fun zigZagKeysCompareByDecodedValue() {
        val schema = CanonicalSchema(mapOf("t" to mapOf(1 to CanonicalField.MapOf(MapKey.ZigZag, null))))
        val plusOne = entry(bytes(0x08, 2)) // zigzag(1) = 2
        val minusOne = entry(bytes(0x08, 1)) // zigzag(-1) = 1
        val minusTwo = entry(bytes(0x08, 3)) // zigzag(-2) = 3
        assertContentEquals(minusTwo + minusOne + plusOne, canonicalize(plusOne + minusTwo + minusOne, "t", schema))
    }

    @Test
    fun mapMessageValuesAreRewritten() {
        val schema = CanonicalSchema(
            mapOf(
                "t" to mapOf(1 to CanonicalField.MapOf(MapKey.Unsigned, "v")),
                "v" to mapOf(1 to s, 2 to s),
            ),
        )
        val input = entry(bytes(0x08, 1, 0x12, 4, 0x10, 2, 0x08, 1))
        assertContentEquals(entry(bytes(0x08, 1, 0x12, 4, 0x08, 1, 0x10, 2)), canonicalize(input, "t", schema))
    }

    @Test
    fun aTypeMissingFromTheSchemaFails() {
        val schema = CanonicalSchema(mapOf("outer" to mapOf(1 to CanonicalField.Message("inner"))))
        assertFailsWith<IllegalArgumentException> { canonicalize(bytes(0x0a, 2, 0x08, 1), "outer", schema) }
    }

    /** A map field 1 entry wrapping [body]. */
    private fun entry(body: ByteArray): ByteArray = bytes(0x0a, body.size) + body

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
