@file:JsModule("@noble/ed25519")
@file:JsNonModule

package com.getcode.ed25519kmp

import org.khronos.webgl.Uint8Array

// The parts of @noble/ed25519 the JS actual calls. noble takes and returns Uint8Array; a Kotlin
// ByteArray is an Int8Array, so Ed25519Kmp.js.kt converts at the boundary.

internal external object hashes {
    var sha512: ((Uint8Array) -> Uint8Array)?
}

internal external object etc {
    fun mod(a: dynamic, md: dynamic = definedExternally): dynamic
}

internal external object utils {
    fun getExtendedPublicKey(secretKey: Uint8Array): ExtendedKey
}

internal external interface ExtendedKey {
    val head: Uint8Array
    val prefix: Uint8Array
    val pointBytes: Uint8Array
}

internal external class Point {
    fun multiply(n: dynamic, safe: Boolean = definedExternally): Point
    fun toBytes(): Uint8Array

    companion object {
        val BASE: Point
        fun fromBytes(bytes: Uint8Array, zip215: Boolean = definedExternally): Point
        fun CURVE(): dynamic
    }
}

internal external interface VerifyOptions {
    var zip215: Boolean?
}

internal external fun verify(
    signature: Uint8Array,
    message: Uint8Array,
    publicKey: Uint8Array,
    options: VerifyOptions = definedExternally,
): Boolean
