package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

@InternalOpenCodeApi public class AssociatedTokenProgram {

    enum class Command(val value: Byte) {
        create(0),
        createIdempotent(1),
        ;
    }

    companion object {
        val address = PublicKey(
            Base58.decode("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL").toList()
        )
    }
}