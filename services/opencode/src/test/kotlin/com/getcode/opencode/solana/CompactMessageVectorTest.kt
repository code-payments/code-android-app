package com.getcode.opencode.solana

import com.getcode.solana.keys.PublicKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GATE: this repo's intent-signing "compact message" (the bytes signed per action) must match the
 * canonical cross-platform vectors. The iOS repo asserts the identical vectors — matching guarantees
 * both apps sign the SAME payload for a transfer/withdraw (a divergence = one platform producing a
 * signature the server rejects). The message LAYOUT (field order, "transfer" domain, little-endian
 * amount) is verified identical by source inspection on both sides; this test gates the byte
 * composition (pubkey serialization + LE amount) and SHA-256. The signature is ed25519 over the hash —
 * already gated by ed25519.json.
 *
 * Pure-JVM unit test. Fixture synced from `code/test-vectors/`.
 */
class CompactMessageVectorTest {

    private fun key(seed: Int): PublicKey = PublicKey(ByteArray(32) { seed.toByte() }.toList())

    private fun amountLe8(value: String): List<Byte> {
        val v = value.toULong()
        return (0 until 8).map { ((v shr (8 * it)) and 0xFFu).toByte() }
    }

    @Test
    fun compact_message_matches_canonical_vectors() {
        val text = javaClass.getResourceAsStream("/compact_message.json")!!.bufferedReader().use { it.readText() }
        val vectors = Json.parseToJsonElement(text).jsonObject["vectors"]!!.jsonArray
        assertTrue(vectors.isNotEmpty(), "no vectors loaded")

        for (el in vectors) {
            val v = el.jsonObject
            val name = v["name"]!!.jsonPrimitive.content
            val msg = mutableListOf<Byte>()
            msg.addAll(v["domain"]!!.jsonPrimitive.content.toByteArray(Charsets.UTF_8).toList())
            msg.addAll(key(v["sourceSeed"]!!.jsonPrimitive.int).bytes)
            msg.addAll(key(v["destinationSeed"]!!.jsonPrimitive.int).bytes)
            v["amount"]!!.jsonPrimitive.let { if (it.content != "null") msg.addAll(amountLe8(it.content)) }
            msg.addAll(key(v["nonceSeed"]!!.jsonPrimitive.int).bytes)
            msg.addAll(key(v["nonceValueSeed"]!!.jsonPrimitive.int).bytes)

            val bytes = msg.toByteArray()
            assertEquals(v["message"]!!.jsonPrimitive.content, bytes.toHex(), "message bytes mismatch for $name")
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            assertEquals(v["sha256"]!!.jsonPrimitive.content, digest.toHex(), "sha256 mismatch for $name")
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
