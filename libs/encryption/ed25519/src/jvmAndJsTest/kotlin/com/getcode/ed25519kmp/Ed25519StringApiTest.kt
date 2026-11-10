package com.getcode.ed25519kmp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the string-keyed [Ed25519] API to the same base64 on every target that has it, so a seed
 * or key stored on Android reads back on Node. Expected strings come from RFC 8032 test 1 and
 * Python's base64 (whose `encodebytes` matches `android.util.Base64.DEFAULT`).
 */
class Ed25519StringApiTest {

    private val seedBase64 = "nWGxne/9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A="
    private val publicKeyBase64 = "11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo="

    @Test
    fun key_pair_from_base64_seed_has_rfc8032_public_key() {
        val pair = Ed25519.createKeyPair(seedBase64)
        assertEquals(publicKeyBase64, pair.publicKey)
        assertEquals(seedBase64, pair.seed)
        assertEquals(64, pair.privateKeyBytes.size)
    }

    @Test
    fun seed_from_bytes_uses_the_android_default_format() {
        val seed = decode(seedBase64)
        assertEquals("$seedBase64\n", Ed25519.createKeyPair(seed).seed)
        assertEquals(
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4\nOTo7\n",
            encodeDefault(ByteArray(60) { it.toByte() }),
        )
    }

    @Test
    fun decode_skips_newlines_and_accepts_missing_padding() {
        val bytes = ByteArray(60) { it.toByte() }
        assertContentEquals(bytes, decode(encodeDefault(bytes)))
        assertContentEquals(decode(seedBase64), decode(seedBase64.trimEnd('=')))
    }

    @Test
    fun sign_and_verify_through_the_key_pair() {
        val pair = Ed25519.createKeyPair(seedBase64)
        val message = "flipcash".encodeToByteArray()
        val signature = pair.sign(message)
        assertTrue(pair.verify(signature, message))
        assertFalse(pair.verify(signature, "flipcasH".encodeToByteArray()))
    }

    @Test
    fun random_seeds_have_the_requested_size_and_differ() {
        val a = Ed25519.createSeed32()
        val b = Ed25519.createSeed32()
        assertEquals(32, a.size)
        assertEquals(16, Ed25519.createSeed16().size)
        assertFalse(a.contentEquals(b))
    }
}
