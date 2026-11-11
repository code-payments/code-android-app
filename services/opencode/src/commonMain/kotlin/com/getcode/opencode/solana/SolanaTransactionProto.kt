package com.getcode.opencode.solana

import com.google.protobuf.ByteString

fun SolanaTransaction.Companion.fromBytes(bytes: ByteString): SolanaTransaction? {
    return fromList(bytes.toByteArray().toList())
}
