package com.getcode.ed25519kmp

import com.getcode.libs.parcelable.ManualParcelable
import com.getcode.libs.parcelable.Parcel
import com.getcode.libs.parcelable.parcelableCreator
import java.util.Base64

/**
 * The string-keyed Ed25519 API the app was written against, shared by Android and desktop.
 *
 * Mirrors the JNI class `com.getcode.ed25519.Ed25519` member for member, so callers only change
 * the import. Key strings keep the JNI's format (unwrapped base64), and a seed made from bytes keeps
 * the `Base64.DEFAULT` format the JNI class stored, so serialized seeds and parcels are unchanged.
 */
object Ed25519 {

    fun createKeyPair(): KeyPair = createKeyPair(createSeed32())

    /** [seed] is base64, as produced by [KeyPair.seed]. */
    fun createKeyPair(seed: String): KeyPair {
        val pair = Ed25519Kmp.createKeyPair(decode(seed))
        return KeyPair(encode(pair.publicKey), encode(pair.privateKey), seed)
    }

    fun createKeyPair(seed: ByteArray): KeyPair {
        val pair = Ed25519Kmp.createKeyPair(seed)
        return KeyPair(encode(pair.publicKey), encode(pair.privateKey), encodeDefault(seed))
    }

    fun createSeed16(): ByteArray = randomSeed(16)

    fun createSeed32(): ByteArray = randomSeed(32)

    fun sign(message: ByteArray, keyPair: KeyPair): ByteArray =
        Ed25519Kmp.sign(message, keyPair.publicKeyBytes, keyPair.privateKeyBytes)

    fun verify(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean =
        Ed25519Kmp.verify(signature, message, publicKey)

    fun onCurve(publicKey: ByteArray): Boolean = Ed25519Kmp.onCurve(publicKey)

    class KeyPair(
        val publicKey: String,
        val privateKey: String,
        val seed: String?,
    ) : ManualParcelable {

        val publicKeyBytes: ByteArray get() = decode(publicKey)

        val privateKeyBytes: ByteArray get() = decode(privateKey)

        fun sign(message: ByteArray): ByteArray = Ed25519.sign(message, this)

        fun verify(signature: ByteArray, message: ByteArray): Boolean =
            Ed25519.verify(signature, message, publicKeyBytes)

        override fun equals(other: Any?): Boolean =
            other is KeyPair && publicKey == other.publicKey && privateKey == other.privateKey

        override fun hashCode(): Int = java.util.Objects.hash(publicKey, privateKey)

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeString(publicKey)
            dest.writeString(privateKey)
            dest.writeString(seed)
        }

        companion object {
            @JvmField
            val CREATOR: Any = parcelableCreator({ arrayOfNulls<KeyPair>(it) }) { parcel ->
                KeyPair(checkNotNull(parcel.readString()), checkNotNull(parcel.readString()), parcel.readString())
            }
        }
    }
}

internal expect fun randomSeed(size: Int): ByteArray

private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

// android.util.Base64.DEFAULT: 76-character lines, each ending in "\n".
private fun encodeDefault(bytes: ByteArray): String =
    Base64.getMimeEncoder(76, byteArrayOf('\n'.code.toByte())).encodeToString(bytes) + "\n"

private fun decode(base64: String): ByteArray = Base64.getMimeDecoder().decode(base64)
