package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

class TokenProgram {
    companion object {
        val address = PublicKey(Base58.decode("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA").toList())
    }
}