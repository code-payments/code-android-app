package com.getcode.solana.keys

import com.google.protobuf.ByteString

/**
 * `ByteString` is a proto-boundary type, not something the key/signature value types themselves
 * should depend on. These sit in their own file, outside [Signature] and [PublicKey.Companion],
 * so that neither type carries a `ByteString` import.
 */

/**
 * Pseudo-constructor for [Signature] from a proto [ByteString]. Kotlin resolves a top-level
 * function sharing a class's name alongside that class's real constructors, so call sites written
 * as `Signature(byteString)` keep compiling unchanged.
 */
fun Signature(byteString: ByteString): Signature = Signature(byteString.toByteArray().toList())

fun PublicKey.Companion.fromByteString(byteString: ByteString): PublicKey {
    return PublicKey(byteString.toByteArray().toList())
}
