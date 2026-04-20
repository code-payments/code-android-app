package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.PublicKey

internal class CurrencyCreatorProgram {

    enum class Command(val value: Byte) {
        unknown(0),
        initializeCurrency(1),
        initializePool(2),
        initializeMetadata(3),
        buyTokens(4),
        sellTokens(5),
        buyAndDepositIntoVm(6),
        sellAndDepositIntoVm(7),
        ;
    }

    companion object {
        val address = PublicKey.fromBase58("ccJYP5gjZqcEHaphcxAZvkxCrnTVfYMjyhSYkpQtf8Z")
    }
}