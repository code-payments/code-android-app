package com.getcode.ed25519

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GATE: this repo's ed25519 (JNI) must reproduce the canonical cross-platform fixtures exactly.
 * The iOS repo runs the identical fixtures against its ed25519 — matching outputs on both sides is
 * what guarantees the apps agree. RFC 8032 anchors mean "matches fixture" == "correct".
 *
 * Instrumented (not a host unit test) because ed25519 is a JNI/NDK library and createKeyPair uses
 * android.util.Base64 — both require the Android runtime. Fixtures live in androidTest/assets and are
 * synced from the orchestrator's `code/test-vectors/` (single source of truth).
 */
@RunWith(AndroidJUnit4::class)
class Ed25519VectorTest {

    @Test
    fun ed25519_matches_canonical_vectors() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val json = ctx.assets.open("ed25519.json").bufferedReader().use { it.readText() }
        val vectors = JSONObject(json).getJSONArray("vectors")
        assertTrue("no vectors loaded", vectors.length() > 0)

        for (i in 0 until vectors.length()) {
            val v = vectors.getJSONObject(i)
            val name = v.getString("name")
            val seed = v.getString("seed").hexToBytes()
            val message = v.getString("message").hexToBytes()

            val keyPair = Ed25519.createKeyPair(seed)
            val publicKey = keyPair.publicKeyBytes
            val signature = Ed25519.sign(message, keyPair)

            assertEquals("public key mismatch for $name", v.getString("publicKey"), publicKey.toHex())
            assertEquals("signature mismatch for $name", v.getString("signature"), signature.toHex())
            assertTrue("verify failed for $name", Ed25519.verify(signature, message, publicKey))
        }
    }
}

private fun String.hexToBytes(): ByteArray =
    ByteArray(length / 2) { ((this[it * 2].digitToInt(16) shl 4) or this[it * 2 + 1].digitToInt(16)).toByte() }

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
