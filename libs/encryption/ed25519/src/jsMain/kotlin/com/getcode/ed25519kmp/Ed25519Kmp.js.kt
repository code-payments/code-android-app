package com.getcode.ed25519kmp

import com.getcode.crypt.Sha512
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import com.getcode.ed25519kmp.verify as nobleVerify

/**
 * JS actual on @noble/ed25519, since the orlp C sources the other targets compile don't reach
 * Kotlin/JS.
 *
 * noble's own `sign` takes the 32-byte seed, but this API signs from orlp's 64-byte expanded key
 * (`clamped scalar || prefix`), from which the seed can't be recovered. So [sign] runs RFC 8032's
 * signing steps itself on noble's point arithmetic, the same steps noble's `sign` runs after it
 * expands the seed.
 *
 * Everything is synchronous: noble's sync API needs `hashes.sha512`, which is set to
 * `:libs:encryption:sha512` on first use. The async API returns Promises, which these `expect`
 * functions can't block on.
 */
actual object Ed25519Kmp {

    init {
        hashes.sha512 = { input -> Sha512.hash(input.toByteArray()).toUint8Array() }
    }

    actual fun createKeyPair(seed: ByteArray): KeyPair {
        require(seed.size == 32) { "seed must be 32 bytes, was ${seed.size}" }
        val extended = utils.getExtendedPublicKey(seed.toUint8Array())
        return KeyPair(
            publicKey = extended.pointBytes.toByteArray(),
            privateKey = extended.head.toByteArray() + extended.prefix.toByteArray(),
        )
    }

    actual fun sign(message: ByteArray, publicKey: ByteArray, privateKey: ByteArray): ByteArray {
        require(publicKey.size == 32) { "publicKey must be 32 bytes, was ${publicKey.size}" }
        require(privateKey.size == 64) { "privateKey must be 64 bytes, was ${privateKey.size}" }

        // r = H(prefix || M) mod L;  R = [r]B
        val r = modL(Sha512.hash(privateKey.copyOfRange(32, 64) + message))
        val rPoint = Point.BASE.multiply(r).toBytes().toByteArray()
        // k = H(R || A || M) mod L;  S = (r + k * s) mod L
        val k = modL(Sha512.hash(rPoint + publicKey + message))
        val s = modL(privateKey.copyOf(32))
        val bigS = etc.mod(r + k * s, ORDER)
        return rPoint + littleEndian32(bigS)
    }

    // Strict RFC 8032 decoding (zip215 = false): canonical encodings only, no small-order keys.
    actual fun verify(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        val options = js("{}").unsafeCast<VerifyOptions>().apply { zip215 = false }
        return runCatching {
            nobleVerify(signature.toUint8Array(), message.toUint8Array(), publicKey.toUint8Array(), options)
        }.getOrDefault(false)
    }

    // Lenient decoding (zip215 = true) accepts non-canonical y >= p and x = 0 with the sign bit set,
    // as the JVM and Android actuals do. Ed25519ConsistencyTest pins both cases.
    actual fun onCurve(publicKey: ByteArray): Boolean =
        publicKey.size == 32 && runCatching { Point.fromBytes(publicKey.toUint8Array(), true) }.isSuccess

    private val ORDER: dynamic = Point.CURVE().n

    // A little-endian byte string as a BigInt, reduced mod the group order L.
    private fun modL(bytes: ByteArray): dynamic {
        val hex = bytes.reversed().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val bigInt: dynamic = js("BigInt")
        return etc.mod(bigInt("0x$hex"), ORDER)
    }

    private fun littleEndian32(value: dynamic): ByteArray {
        val hex = (value.toString(16) as String).padStart(64, '0')
        return ByteArray(32) { hex.substring(62 - it * 2, 64 - it * 2).toInt(16).toByte() }
    }

    private fun ByteArray.toUint8Array(): Uint8Array {
        val view = unsafeCast<Int8Array>()
        return Uint8Array(view.buffer, view.byteOffset, view.length)
    }

    private fun Uint8Array.toByteArray(): ByteArray = Int8Array(buffer, byteOffset, length).unsafeCast<ByteArray>()
}
