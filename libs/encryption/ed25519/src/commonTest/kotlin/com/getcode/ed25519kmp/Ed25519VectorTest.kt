package com.getcode.ed25519kmp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GATE: Ed25519 must reproduce RFC 8032 vectors on BOTH Android (JVM host) and iOS (native).
 * Fixture: src/commonTest/resources/ed25519.json (canonical source: test-vectors/ed25519.json).
 *
 * Vector format:
 *   seed (32-byte hex) + message (hex, may be empty)
 *   → publicKey (32-byte hex) + signature (64-byte hex)
 */
class Ed25519VectorTest {

    @Test
    fun ed25519_createKeyPair_matches_canonical_vectors() {
        val vectors = loadVectors()
        assertTrue(vectors.isNotEmpty(), "no vectors loaded from ed25519.json")

        for (v in vectors) {
            val pair = Ed25519Kmp.createKeyPair(v.seed)
            assertEquals(
                v.publicKey.toHexString(), pair.publicKey.toHexString(),
                "publicKey mismatch for '${v.name}'"
            )
        }
    }

    @Test
    fun ed25519_sign_matches_canonical_vectors() {
        val vectors = loadVectors()
        for (v in vectors) {
            val pair = Ed25519Kmp.createKeyPair(v.seed)
            val sig = Ed25519Kmp.sign(v.message, pair.publicKey, pair.privateKey)
            assertEquals(
                v.signature.toHexString(), sig.toHexString(),
                "signature mismatch for '${v.name}'"
            )
        }
    }

    @Test
    fun ed25519_verify_accepts_canonical_signatures() {
        val vectors = loadVectors()
        for (v in vectors) {
            val pair = Ed25519Kmp.createKeyPair(v.seed)
            val sig = Ed25519Kmp.sign(v.message, pair.publicKey, pair.privateKey)
            assertTrue(
                Ed25519Kmp.verify(sig, v.message, pair.publicKey),
                "verify returned false for '${v.name}'"
            )
        }
    }

    @Test
    fun ed25519_onCurve_true_for_canonical_public_keys() {
        val vectors = loadVectors()
        for (v in vectors) {
            assertTrue(
                Ed25519Kmp.onCurve(v.publicKey),
                "onCurve returned false for '${v.name}'"
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private data class Vector(
        val name: String,
        val seed: ByteArray,
        val message: ByteArray,
        val publicKey: ByteArray,
        val signature: ByteArray,
    )

    private fun loadVectors(): List<Vector> {
        val text = readTestResource("ed25519.json")
        val root = Json.parseToJsonElement(text).jsonObject
        return root["vectors"]!!.jsonArray.map { el ->
            val o = el.jsonObject
            Vector(
                name = o["name"]!!.jsonPrimitive.content,
                seed = o["seed"]!!.jsonPrimitive.content.hexToBytes(),
                message = o["message"]!!.jsonPrimitive.content.hexToBytes(),
                publicKey = o["publicKey"]!!.jsonPrimitive.content.hexToBytes(),
                signature = o["signature"]!!.jsonPrimitive.content.hexToBytes(),
            )
        }
    }

    private fun String.hexToBytes(): ByteArray =
        if (isEmpty()) ByteArray(0)
        else ByteArray(length / 2) { i ->
            ((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16)).toByte()
        }

    private fun ByteArray.toHexString(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
