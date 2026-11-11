package com.getcode.ed25519kmp

import com.getcode.crypt.Sha512
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the behaviour the RFC vectors don't cover, so every target agrees with the JVM actual
 * (`net.i2p.crypto:eddsa`): keys and signatures on random seeds and messages, verify on tampered
 * input, and onCurve on random bytes and edge encodings. onCurve decides PDA derivation, so a
 * target that disagrees derives different addresses.
 *
 * The expected values were captured on the JVM actual. [Random] with a fixed seed yields the same
 * sequence on every Kotlin target, so each target walks the same inputs.
 */
class Ed25519ConsistencyTest {

    @Test
    fun keys_and_signatures_match_the_jvm_actual() {
        val random = Random(0x5eed)
        val all = mutableListOf<Byte>()
        repeat(CASES) {
            val seed = random.nextBytes(32)
            val message = random.nextBytes(random.nextInt(0, 300))
            val pair = Ed25519Kmp.createKeyPair(seed)
            val signature = Ed25519Kmp.sign(message, pair.publicKey, pair.privateKey)
            assertEquals(true, Ed25519Kmp.verify(signature, message, pair.publicKey), "verify, case $it")
            all += (pair.publicKey + pair.privateKey + signature).toList()
        }
        assertEquals(KEYS_AND_SIGNATURES_SHA512, Sha512.hash(all.toByteArray()).hex())
    }

    @Test
    fun verify_rejects_tampered_input() {
        val random = Random(0x7a3e)
        repeat(CASES) { n ->
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

            assertEquals(false, Ed25519Kmp.verify(sig, msg, key), "verify of tampered input, case $n")
        }
    }

    @Test
    fun onCurve_matches_the_jvm_actual_on_random_bytes() {
        val random = Random(0x0c0e)
        val bits = StringBuilder()
        repeat(ON_CURVE_CASES) { bits.append(if (Ed25519Kmp.onCurve(random.nextBytes(32))) '1' else '0') }
        val actual = bits.toString()
        val mismatch = actual.indices.firstOrNull { actual[it] != ON_CURVE_RANDOM.getOrNull(it) }
        assertEquals(null, mismatch, "onCurve differs from the JVM actual at case $mismatch")
        // About half of all 32-byte strings decode to a point; make sure both branches ran.
        check(actual.count { it == '1' } in 200..300) { "unexpected on-curve count" }
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
        val bits = StringBuilder()
        for (y in ys) for (signBit in listOf(0, 0x80)) {
            val bytes = y.hexToBytes().also { it[31] = (it[31].toInt() or signBit).toByte() }
            bits.append(if (Ed25519Kmp.onCurve(bytes)) '1' else '0')
        }
        assertEquals(ON_CURVE_EDGES, bits.toString())
    }

    private fun String.hexToBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private companion object {
        const val CASES = 30
        const val ON_CURVE_CASES = 500

        const val KEYS_AND_SIGNATURES_SHA512 =
            "ce8b9d0e8cda9491d88f036307d83f0d36ea7d4ac4a349bc12bdfe07a3316566" +
                "3676edf05a4551dcf896918c2a0fb66b7454c2731d2daf1db02e4db2acb500cb"
        const val ON_CURVE_RANDOM =
            "0010110011100101110100110000000101000111000110011110010000000010111011001011110011001001110111111001" +
            "1100000100001000101101000100100011110010011010101001001001011110011101001111101011011000110111011001" +
            "1101110110011110111110110101100001001111110101100000010110010000010000000000001010001110010000100100" +
            "1001001110001001011011101111111101100001011000001011100101101101111000100000011110001011111001111010" +
            "0100000111110011111011001001010001110100101000010000100000001010110010000000100000001001111010101010"
        const val ON_CURVE_EDGES = "111111111111"
    }
}
