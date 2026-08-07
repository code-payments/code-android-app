package com.getcode.vendor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GATE: this repo's Base58 must reproduce the canonical cross-platform fixtures exactly (encode and
 * decode). The iOS repo asserts the identical fixtures against its Base58 — matching on both sides is
 * what guarantees the apps agree on address/key encoding. Anchored to the Solana all-ones address.
 *
 * Pure-JVM host unit test (Base58 has no Android/JNI deps). Fixture synced from `code/test-vectors/`.
 */
class Base58VectorTest {

    @Test
    fun base58_matches_canonical_vectors() {
        val text = javaClass.getResourceAsStream("/base58.json")!!.bufferedReader().use { it.readText() }
        val vectors = Json.parseToJsonElement(text).jsonObject["vectors"]!!.jsonArray
        assertTrue(vectors.isNotEmpty(), "no vectors loaded")

        for (el in vectors) {
            val o = el.jsonObject
            val name = o["name"]!!.jsonPrimitive.content
            val bytes = o["bytes"]!!.jsonPrimitive.content.hexToBytes()
            val expected = o["base58"]!!.jsonPrimitive.content

            assertEquals(expected, Base58.encode(bytes), "encode mismatch for $name")
            assertTrue(Base58.decode(expected).contentEquals(bytes), "decode mismatch for $name")
        }
    }
}

private fun String.hexToBytes(): ByteArray =
    if (isEmpty()) ByteArray(0)
    else ByteArray(length / 2) { ((this[it * 2].digitToInt(16) shl 4) or this[it * 2 + 1].digitToInt(16)).toByte() }
