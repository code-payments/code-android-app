package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.LENGTH_32
import com.getcode.solana.keys.PublicKey

class SystemProgram {

    enum class Command {
        createAccount,
        assign,
        transfer,
        createAccountWithSeed,
        advanceNonceAccount,
        withdrawNonceAccount,
        initializeNonceAccount,
        authorizeNonceAccount,
        allocate,
        allocateWithSeed,
        assignWithSeed,
        transferWithSeed,
    }

    companion object { 
        val address =
            PublicKey(ByteArray(LENGTH_32) { 0 }.toList())
    }
}