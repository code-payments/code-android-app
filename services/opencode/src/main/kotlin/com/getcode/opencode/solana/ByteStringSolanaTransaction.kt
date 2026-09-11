package com.getcode.opencode.solana

import com.google.protobuf.ByteString

/**
 * `ByteString` is a proto-boundary type. Kept out of [SolanaTransaction] itself so the class only
 * depends on `ByteArray`/`List<Byte>`; this extension is the one place that bridges to proto wire
 * bytes. Call sites written as `SolanaTransaction.fromBytes(bytes)` keep compiling unchanged since
 * an extension function on the companion object resolves the same way.
 */
fun SolanaTransaction.Companion.fromBytes(bytes: ByteString): SolanaTransaction? {
    return fromList(bytes.toByteArray().toList())
}
