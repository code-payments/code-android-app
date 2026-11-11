package com.getcode.ed25519kmp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

// The system CSPRNG, as SecureRandom is on Android.
@OptIn(ExperimentalForeignApi::class)
internal actual fun randomSeed(size: Int): ByteArray {
    val bytes = ByteArray(size)
    if (size == 0) return bytes
    val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) }
    check(status == errSecSuccess) { "SecRandomCopyBytes failed: $status" }
    return bytes
}
