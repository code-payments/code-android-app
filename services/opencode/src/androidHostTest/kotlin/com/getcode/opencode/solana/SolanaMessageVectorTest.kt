package com.getcode.opencode.solana

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GATE: this repo's Solana legacy-message serialization must reproduce the canonical cross-platform
 * vectors exactly. The iOS repo asserts the identical vectors — matching guarantees both apps produce
 * byte-identical transaction messages (a divergence = a transaction one platform builds that the chain
 * or the other platform would reject). Reference implements the canonical Solana legacy wire format.
 *
 * Pure-JVM unit test (serialization has no Android/JNI deps). Fixture synced from `code/test-vectors/`.
 */
class SolanaMessageVectorTest {

    private fun key(seed: Int): PublicKey = PublicKey(ByteArray(32) { seed.toByte() }.toList())

    private fun meta(seed: Int, role: String): AccountMeta = when (role) {
        "payer" -> AccountMeta.payer(key(seed))
        "writable" -> AccountMeta.writable(key(seed))
        "writable-signer" -> AccountMeta.writable(key(seed), signer = true)
        "readonly", "readonly-program" -> AccountMeta.readonly(key(seed))
        "readonly-signer" -> AccountMeta.readonly(key(seed), signer = true)
        else -> error("unknown role $role")
    }

    @Test
    fun message_matches_canonical_vectors() {
        val text = javaClass.getResourceAsStream("/solana_message.json")!!.bufferedReader().use { it.readText() }
        val vectors = Json.parseToJsonElement(text).jsonObject["vectors"]!!.jsonArray
        assertTrue(vectors.isNotEmpty(), "no vectors loaded")

        for (el in vectors) {
            val v = el.jsonObject
            val name = v["name"]!!.jsonPrimitive.content
            val accounts = v["accounts"]!!.jsonArray.map {
                meta(it.jsonObject["seed"]!!.jsonPrimitive.int, it.jsonObject["role"]!!.jsonPrimitive.content)
            }
            val blockhash: Hash = key(v["blockhashSeed"]!!.jsonPrimitive.int)
            val instructions = v["instructions"]!!.jsonArray.map {
                val io = it.jsonObject
                Instruction(
                    program = key(io["programSeed"]!!.jsonPrimitive.int),
                    // flags are irrelevant here — compile() only uses the pubkey to resolve indexes
                    accounts = io["accountSeeds"]!!.jsonArray.map { s -> AccountMeta.readonly(key(s.jsonPrimitive.int)) },
                    data = io["data"]!!.jsonPrimitive.content.hexToBytes(),
                )
            }
            val encoded = LegacyMessage.newInstance(accounts, blockhash, instructions).encode()
            assertEquals(v["expectedMessage"]!!.jsonPrimitive.content, encoded.toHex(), "message mismatch for $name")
        }
    }
}

private fun String.hexToBytes(): List<Byte> =
    if (isEmpty()) emptyList() else chunked(2).map { it.toInt(16).toByte() }

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
