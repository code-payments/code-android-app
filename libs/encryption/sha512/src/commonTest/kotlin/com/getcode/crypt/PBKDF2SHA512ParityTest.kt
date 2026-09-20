package com.getcode.crypt

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Holds [PBKDF2SHA512] to [PBKDF2SHA512Reference] byte for byte.
 *
 * On a target with no platform implementation these compare the reference against itself and pass
 * trivially. That is the point: the same cases run everywhere, and on a target that does route to a
 * platform provider they become its conformance check. The vectors in [PBKDF2SHA512Test] cover
 * agreement with the published RFC answers; these cover agreement between the two paths on inputs
 * the vectors do not reach — non-ASCII on either side, empty salt, a short derived key, and the
 * 2048-round BIP39 shape this module exists to serve.
 */
class PBKDF2SHA512ParityTest {

    private fun assertMatchesReference(P: String, S: String, c: Int, dkLen: Int) {
        assertContentEquals(
            PBKDF2SHA512Reference.derive(P, S, c, dkLen),
            PBKDF2SHA512.derive(P, S, c, dkLen),
            "platform output diverged from the reference for P=$P S=$S c=$c dkLen=$dkLen",
        )
    }

    @Test
    fun bip39SeedShape() {
        // The real call: 12 BIP39 words, "mnemonic" salt, 2048 rounds, 64-byte seed.
        assertMatchesReference(
            P = "legal winner thank year wave sausage worth useful legal winner thank yellow",
            S = "mnemonic",
            c = 2048,
            dkLen = 64,
        )
    }

    @Test
    fun bip39SeedShapeWithPassphraseInSalt() {
        // A derive path with a password puts it in the salt, not the PBKDF2 password.
        assertMatchesReference(
            P = "legal winner thank year wave sausage worth useful legal winner thank yellow",
            S = "mnemonicpool-7665",
            c = 2048,
            dkLen = 64,
        )
    }

    @Test
    fun nonAsciiSaltMatches() {
        // The salt crosses as bytes, so a platform implementation is expected to handle it.
        assertMatchesReference(P = "password", S = "sält-ünïcode-☃", c = 64, dkLen = 64)
    }

    @Test
    fun nonAsciiPasswordMatches() {
        // A char[] password is where providers disagree; the Android implementation declines these
        // and falls back. Either way the answer must be the reference's.
        assertMatchesReference(P = "pässwörd-☃", S = "salt", c = 64, dkLen = 64)
    }

    @Test
    fun emptySaltMatches() {
        assertMatchesReference(P = "password", S = "", c = 64, dkLen = 64)
    }

    @Test
    fun derivedKeyShorterThanOneBlockMatches() {
        assertMatchesReference(P = "password", S = "salt", c = 64, dkLen = 20)
    }

    @Test
    fun derivedKeyLongerThanOneBlockMatches() {
        assertMatchesReference(P = "password", S = "salt", c = 64, dkLen = 100)
    }

    @Test
    fun referenceStillMatchesPublishedVector() {
        // The fallback has to stay correct on its own, not only relative to the platform.
        val hex = PBKDF2SHA512Reference.derive("password", "salt", 1, 64)
            .joinToString("") { b ->
                val v = b.toInt() and 0xFF
                "0123456789abcdef"[v ushr 4].toString() + "0123456789abcdef"[v and 0x0F]
            }
        assertEquals(
            "867f70cf1ade02cff3752599a3a53dc4" +
                "af34c7a669815ae5d513554e1c8cf252" +
                "c02d470a285a0501bad999bfe943c08f" +
                "050235d7d68b1da55e63f73b60a57fce",
            hex,
        )
    }
}
