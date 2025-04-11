package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

internal class AssociatedTokenProgram {
    companion object {
        val address = PublicKey(
            Base58.decode("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL").toList()
        )
    }
}