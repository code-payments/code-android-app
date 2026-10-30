@file:OptIn(ExperimentalUnsignedTypes::class)

package com.flipcash.app.devicelink

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.aead.AuthenticatedEncryptionWithAssociatedData
import com.ionspin.kotlin.crypto.generichash.GenericHash
import com.ionspin.kotlin.crypto.keyexchange.KeyExchange
import com.ionspin.kotlin.crypto.util.LibsodiumRandom

/**
 * Keys and codes for one link. The phone is libsodium's kx client and the desktop its server.
 *
 * The transcript binds both public keys and the QR secret. The short code is a keyed hash of the
 * transcript under the phone-to-desktop session key, so it can only match when both sides hold the
 * same key: an attacker who swaps the phone's public key on its way to the desktop ends up with a
 * different key and can't predict the phone's code, so grinding keys for a collision doesn't help.
 */
internal object LinkCrypto {
    const val KEY_BYTES = 32
    const val SECRET_BYTES = 16
    private const val NONCE_BYTES = 24
    private val LABEL = "flipcash-link-v1".encodeToByteArray()

    class Ephemeral(val publicKey: ByteArray, val secretKey: ByteArray) {
        fun wipe() = secretKey.fill(0)
    }

    class Session(
        val phoneToDesktop: ByteArray,
        val desktopToPhone: ByteArray,
        val transcript: ByteArray,
    ) {
        fun wipe() {
            phoneToDesktop.fill(0)
            desktopToPhone.fill(0)
        }
    }

    fun ephemeral(): Ephemeral {
        ensureSodium()
        val pair = KeyExchange.keypair()
        return Ephemeral(pair.publicKey.toByteArray(), pair.secretKey.toByteArray())
    }

    fun secret(): ByteArray {
        ensureSodium()
        return LibsodiumRandom.buf(SECRET_BYTES).toByteArray()
    }

    fun phoneSession(phone: Ephemeral, desktopKey: ByteArray, secret: ByteArray): Session {
        ensureSodium()
        val keys = KeyExchange.clientSessionKeys(
            phone.publicKey.toUByteArray(), phone.secretKey.toUByteArray(), desktopKey.toUByteArray(),
        )
        return Session(
            phoneToDesktop = keys.sendKey.toByteArray(),
            desktopToPhone = keys.receiveKey.toByteArray(),
            transcript = transcript(desktopKey, phone.publicKey, secret),
        )
    }

    fun desktopSession(desktop: Ephemeral, phoneKey: ByteArray, secret: ByteArray): Session {
        ensureSodium()
        val keys = KeyExchange.serverSessionKeys(
            desktop.publicKey.toUByteArray(), desktop.secretKey.toUByteArray(), phoneKey.toUByteArray(),
        )
        return Session(
            phoneToDesktop = keys.receiveKey.toByteArray(),
            desktopToPhone = keys.sendKey.toByteArray(),
            transcript = transcript(desktop.publicKey, phoneKey, secret),
        )
    }

    /** Six digits both screens show; the user confirms they match on the phone before the seed is sent. */
    fun shortCode(session: Session): String {
        ensureSodium()
        val h = GenericHash.genericHash(
            session.transcript.toUByteArray(), 32, session.phoneToDesktop.toUByteArray(),
        )
        val n = ((h[0].toLong() shl 24) or (h[1].toLong() shl 16) or (h[2].toLong() shl 8) or h[3].toLong())
        return (n % 1_000_000).toString().padStart(6, '0')
    }

    fun seal(key: ByteArray, plaintext: ByteArray, transcript: ByteArray): ByteArray {
        ensureSodium()
        return seal(key, plaintext, transcript, LibsodiumRandom.buf(NONCE_BYTES).toByteArray())
    }

    /** Fixed-nonce form for the vectors only; production always draws a fresh nonce. */
    internal fun seal(key: ByteArray, plaintext: ByteArray, transcript: ByteArray, nonce: ByteArray): ByteArray {
        require(nonce.size == NONCE_BYTES)
        ensureSodium()
        val ct = AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfEncrypt(
            plaintext.toUByteArray(), transcript.toUByteArray(), nonce.toUByteArray(), key.toUByteArray(),
        )
        return nonce + ct.toByteArray()
    }

    /** Null when the ciphertext was tampered with or sealed under another key or transcript. */
    fun open(key: ByteArray, sealed: ByteArray, transcript: ByteArray): ByteArray? {
        if (sealed.size <= NONCE_BYTES) return null
        ensureSodium()
        return runCatching {
            AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfDecrypt(
                sealed.copyOfRange(NONCE_BYTES, sealed.size).toUByteArray(),
                transcript.toUByteArray(),
                sealed.copyOfRange(0, NONCE_BYTES).toUByteArray(),
                key.toUByteArray(),
            ).toByteArray()
        }.getOrNull()
    }

    private fun transcript(desktopKey: ByteArray, phoneKey: ByteArray, secret: ByteArray) =
        LABEL + desktopKey + phoneKey + secret

    private fun ensureSodium() {
        // Synchronous on JVM/Android/Native, as in chat-cipher.
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initializeWithCallback { }
    }
}
