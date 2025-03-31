package com.getcode.solana.instructions.programs

import com.getcode.vendor.Base58

class AssociatedTokenProgram {
    companion object {
        val address = com.getcode.solana.keys.PublicKey(
            Base58.decode("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL").toList()
        )
    }
}