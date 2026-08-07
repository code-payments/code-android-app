package com.getcode.vendor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GATE: Base58 must reproduce the canonical cross-platform fixtures exactly.
 * These vectors are asserted on both Android (JVM) and iOS (native) so any
 * divergence between platforms is caught immediately.
 *
 * Fixture anchored to `test-vectors/base58.json` (canonical source).
 */
class Base58VectorTest {

    @Test
    fun base58_matches_canonical_vectors() {
        val text = readTestResource("base58.json")
        val root = Json.parseToJsonElement(text).jsonObject
        val vectors = root["vectors"]!!.jsonArray
        assertTrue(vectors.isNotEmpty(), "no vectors loaded")

        for (el in vectors) {
            val o = el.jsonObject
            val name = o["name"]!!.jsonPrimitive.content
            val bytes = o["bytes"]!!.jsonPrimitive.content.hexToBytes()
            val expected = o["base58"]!!.jsonPrimitive.content

            assertEquals(expected, Base58.encode(bytes), "encode mismatch for '$name'")
            assertTrue(
                Base58.decode(expected).contentEquals(bytes),
                "decode mismatch for '$name'"
            )
        }
    }
}

private fun String.hexToBytes(): ByteArray =
    if (isEmpty()) ByteArray(0)
    else ByteArray(length / 2) { i ->
        ((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16)).toByte()
    }
