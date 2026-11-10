package com.getcode.ed25519kmp

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Diffs [Ed25519Reference], the port the JS target runs, against the desktop actual
 * (`net.i2p.crypto:eddsa`) on random inputs, so the behaviour the RFC vectors don't pin also
 * matches: onCurve on arbitrary bytes, and verify on tampered signatures.
 *
 * The seed is fixed so a failure reproduces.
 */
class Ed25519ReferenceDifferentialTest {

    private val random = Random(0x5eed)

    @Test
    fun keys_and_signatures_match_the_jvm_actual() {
        repeat(300) { n ->
            val seed = random.nextBytes(32)
            val message = random.nextBytes(random.nextInt(0, 300))

            val expected = Ed25519Kmp.createKeyPair(seed)
            val actual = Ed25519Reference.createKeyPair(seed)
            assertEquals(expected, actual, "key pair, case $n")

            val signature = Ed25519Kmp.sign(message, expected.publicKey, expected.privateKey)
            assertContentEquals(
                signature,
                Ed25519Reference.sign(message, actual.publicKey, actual.privateKey),
                "signature, case $n",
            )
            assertEquals(true, Ed25519Reference.verify(signature, message, actual.publicKey), "verify, case $n")
        }
    }

    @Test
    fun verify_rejects_what_the_jvm_actual_rejects() {
        repeat(300) { n ->
            val pair = Ed25519Kmp.createKeyPair(random.nextBytes(32))
            val message = random.nextBytes(random.nextInt(1, 100))
            val signature = Ed25519Kmp.sign(message, pair.publicKey, pair.privateKey)

            // Flip one random bit in the signature, the message or the key.
            val sig = signature.copyOf()
            val msg = message.copyOf()
            val key = pair.publicKey.copyOf()
            val target = when (n % 3) { 0 -> sig; 1 -> msg; else -> key }
            val bit = random.nextInt(target.size * 8)
            target[bit / 8] = (target[bit / 8].toInt() xor (1 shl (bit % 8))).toByte()

            assertEquals(
                Ed25519Kmp.verify(sig, msg, key),
                Ed25519Reference.verify(sig, msg, key),
                "verify of tampered input, case $n",
            )
        }
    }

    @Test
    fun onCurve_matches_the_jvm_actual_on_random_bytes() {
        var onCurve = 0
        repeat(3000) { n ->
            val bytes = random.nextBytes(32)
            val expected = Ed25519Kmp.onCurve(bytes)
            assertEquals(expected, Ed25519Reference.onCurve(bytes), "onCurve, case $n: ${bytes.hex()}")
            if (expected) onCurve++
        }
        // About half of all 32-byte strings decode to a point; make sure both branches ran.
        check(onCurve in 1200..1800) { "unexpected on-curve count $onCurve of 3000" }
    }

    @Test
    fun onCurve_matches_the_jvm_actual_on_edge_encodings() {
        // Little-endian y values around 0, 1 and p = 2^255 - 19, including non-canonical y >= p,
        // each with the x sign bit clear and set.
        val ys = listOf(
            "00".repeat(32),
            "01" + "00".repeat(31),
            "ec" + "ff".repeat(30) + "7f", // p - 1
            "ed" + "ff".repeat(30) + "7f", // p (non-canonical 0)
            "ee" + "ff".repeat(30) + "7f", // p + 1 (non-canonical 1)
            "ff".repeat(31) + "7f", // 2^255 - 1
        )
        for (y in ys) for (signBit in listOf(0, 0x80)) {
            val bytes = y.hexToBytes().also { it[31] = (it[31].toInt() or signBit).toByte() }
            assertEquals(Ed25519Kmp.onCurve(bytes), Ed25519Reference.onCurve(bytes), "onCurve ${bytes.hex()}")
        }
    }

    private fun String.hexToBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
