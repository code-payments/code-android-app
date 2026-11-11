package com.getcode.ed25519kmp

// Web Crypto's CSPRNG, global in Node 19+ and in browsers. A Kotlin ByteArray is an Int8Array,
// which getRandomValues fills in place.
internal actual fun randomSeed(size: Int): ByteArray {
    val bytes = ByteArray(size)
    val crypto: dynamic = js("globalThis.crypto")
    crypto.getRandomValues(bytes)
    return bytes
}
