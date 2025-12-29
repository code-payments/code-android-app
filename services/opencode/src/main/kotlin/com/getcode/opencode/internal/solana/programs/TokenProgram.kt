package com.getcode.opencode.internal.solana.programs

import com.getcode.vendor.Base58

internal class TokenProgram {

    enum class Command(val value: Byte) {
        closeAccount(9),
        ;
    }
    companion object {
        val address = com.getcode.solana.keys.PublicKey(
            Base58.decode("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA").toList()
        )
    }
}