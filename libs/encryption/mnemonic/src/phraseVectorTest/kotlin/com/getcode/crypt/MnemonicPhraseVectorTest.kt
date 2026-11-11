package com.getcode.crypt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs slip10.json through [MnemonicPhrase.getSolanaKeyPair], the path apps use, on the JVM and Node.
 * Android runs the same check as a device test (Base64 and Ed25519 are not mockable on the host).
 * Slip10DerivationVectorTest checks derivation on its own; this adds the base64
 * hand-off and the string-keyed Ed25519 keypair that sit behind MnemonicPhrase.
 */
class MnemonicPhraseVectorTest {

    @Test
    fun phrase_keypair_matches_canonical_vectors() {
        val vectors = Json.parseToJsonElement(readTestResource("slip10.json")).jsonObject["vectors"]!!.jsonArray
        assertTrue(vectors.isNotEmpty(), "no vectors loaded from slip10.json")

        for (el in vectors) {
            val v = el.jsonObject
            val name = v["name"]!!.jsonPrimitive.content
            val words = v["mnemonic"]!!.jsonPrimitive.content.split(" ")
            val path = DerivePath.newInstance(v["path"]!!.jsonPrimitive.content)!!

            val keyPair = MnemonicPhrase.newInstance(words)!!.getSolanaKeyPair(path)

            val publicKey = keyPair.publicKeyBytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            assertEquals(v["publicKey"]!!.jsonPrimitive.content, publicKey, "publicKey mismatch for '$name'")
        }
    }
}
