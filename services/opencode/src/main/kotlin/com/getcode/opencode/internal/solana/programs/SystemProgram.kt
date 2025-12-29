package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.LENGTH_32
import com.getcode.solana.keys.PublicKey

internal class SystemProgram {

    enum class Command(val value: Int) {
        createAccount(0),
        assign(1),
        transfer(2),
        createAccountWithSeed(3),
        advanceNonceAccount(4),
        withdrawNonceAccount(5),
        initializeNonceAccount(6),
        authorizeNonceAccount(7),
        allocate(8),
        allocateWithSeed(9),
        assignWithSeed(10),
        transferWithSeed(11),
        ;
    }

    companion object { 
        val address =
            PublicKey(ByteArray(LENGTH_32) { 0 }.toList())
    }
}