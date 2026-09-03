package com.getcode.crypt

import com.getcode.ed25519kmp.Ed25519Kmp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GATE: BIP39 seed -> SLIP-0010 ed25519 derivation must reproduce canonical vectors on BOTH
 * Android (JVM host) and iOS (native).
 * Fixture: src/commonTest/resources/slip10.json (canonical source: test-vectors/slip10.json).
 */
class Slip10DerivationVectorTest {

    @Test
    fun derivation_matches_canonical_vectors() {
        val vectors = loadVectors()
        assertTrue(vectors.isNotEmpty(), "no vectors loaded from slip10.json")

        for (v in vectors) {
            val seed = MnemonicCode.toSeed(v.mnemonic.split(" "), v.passphrase)
            val path = DerivePath.newInstance(v.path)!!
            val derivedKey = Derive.derivedKey(seed, path)
            assertEquals(v.derivedKey, derivedKey.toHex(), "derivedKey mismatch for '${v.name}'")

            val keyPair = Ed25519Kmp.createKeyPair(derivedKey)
            assertEquals(v.publicKey, keyPair.publicKey.toHex(), "publicKey mismatch for '${v.name}'")
        }
    }

    private data class Vector(
        val name: String,
        val mnemonic: String,
        val passphrase: String,
        val path: String,
        val derivedKey: String,
        val publicKey: String,
    )

    private fun loadVectors(): List<Vector> {
        val text = readTestResource("slip10.json")
        val root = Json.parseToJsonElement(text).jsonObject
        return root["vectors"]!!.jsonArray.map { el ->
            val o = el.jsonObject
            Vector(
                name = o["name"]!!.jsonPrimitive.content,
                mnemonic = o["mnemonic"]!!.jsonPrimitive.content,
                passphrase = o["passphrase"]!!.jsonPrimitive.content,
                path = o["path"]!!.jsonPrimitive.content,
                derivedKey = o["derivedKey"]!!.jsonPrimitive.content,
                publicKey = o["publicKey"]!!.jsonPrimitive.content,
            )
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
