package com.getcode.solana.keys

import com.getcode.utils.encodeBase64

/**
 * `com.getcode.utils.encodeBase64` (`:libs:encryption:utils`) is Android-only, so these two
 * extensions can't live in `Key.kt`'s commonMain alongside [KeyType.base58]/[base58Redacted]. They
 * have no production call sites today (grep across the repo finds none outside this declaration),
 * so androidMain is a mechanical relocation, not a behaviour change: any Android call site that
 * used `KeyType.base64()`/`base64Redacted()` before this module became KMP keeps compiling and
 * keeps producing the same string.
 */
fun KeyType.base64(): String = bytes.toByteArray().encodeBase64()
fun KeyType.base64Redacted(): String = base64().redact(visibleLength = 8)
