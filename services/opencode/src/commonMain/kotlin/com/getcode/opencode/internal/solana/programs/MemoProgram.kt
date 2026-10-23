package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

internal class MemoProgram {

    enum class Command(val value: Byte) {
        memo(0),
        ;
    }

    companion object {
        val address = PublicKey(
            Base58.decode("Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo").toList()
        )
    }
}