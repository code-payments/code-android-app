package com.getcode.crypt

/**
 * Apple targets stay on [PBKDF2SHA512Reference] for now.
 *
 * iOS is a real caller, not a hypothetical one. `PBKDF.deriveKey` in FlipcashCore calls
 * `SharedHash.pbkdf2SHA512`, which is a Swift shim over this object in `SharedCoreKit`, and that is
 * the BIP39 seed function for the iOS app. So this null decides whether iOS derives its seed through
 * 2048 rounds of pure Kotlin or through something native.
 *
 * Declining is not a regression: pure Kotlin is what this module offered on every target before the
 * Android path existed, so iOS runs exactly the code it ran before. But `CCKeyDerivationPBKDF` would
 * do here what Conscrypt does on Android, and the Kotlin/Native cost is unmeasured — the
 * ~1ms-per-derivation figure quoted in the iOS `Regression_6a008a1` test describes the CommonCrypto
 * implementation that `PBKDF.swift` replaced, not this one. Measure on iOS before assuming it is cheap.
 */
internal actual fun derivePlatform(P: String, S: String, c: Int, dkLen: Int): ByteArray? = null
