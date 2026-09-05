package com.getcode.crypt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GATE: this repo's BIP39 + SLIP-0010 (ed25519) key derivation must reproduce the canonical
 * cross-platform fixtures exactly. The iOS repo asserts the identical fixtures — matching on both
 * sides guarantees the apps derive the SAME account from a mnemonic (divergence would mean a user's
 * key differs by platform). Anchored to the official BIP39 + SLIP-0010 test vectors.
 *
 * Instrumented: derivation loads the BIP-39 wordlist from res/raw (MnemonicCache) and finishes with
 * the JNI ed25519 keypair — both need the Android runtime. Fixture synced from `code/test-vectors/`.
 */
@RunWith(AndroidJUnit4::class)
class Slip10DerivationVectorTest {

    @Test
    fun derivation_matches_canonical_vectors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        MnemonicCache.init(instrumentation.targetContext) // load BIP-39 wordlist (res/raw/english.txt)

        val json = instrumentation.context.assets.open("slip10.json").bufferedReader().use { it.readText() }
        val vectors = JSONObject(json).getJSONArray("vectors")
        assertTrue("no vectors loaded", vectors.length() > 0)

        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val name = v.getString("name")
            val words = v.getString("mnemonic").split(" ")
            val path = DerivePath.newInstance(v.getString("path"))!!

            val keyPair = MnemonicPhrase.newInstance(words)!!.getSolanaKeyPair(path)

            assertEquals("public key mismatch for $name", v.getString("publicKey"), keyPair.publicKeyBytes.toHex())
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
