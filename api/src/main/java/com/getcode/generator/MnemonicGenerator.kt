package com.getcode.generator

import com.getcode.crypt.MnemonicPhrase
import com.getcode.utils.Base58String
import com.getcode.utils.Base64String
import javax.inject.Inject

class MnemonicGenerator @Inject constructor(
): Generator<Base64String, MnemonicPhrase> {

    override fun generate(predicate: Base64String): MnemonicPhrase {
        return MnemonicPhrase.fromEntropyB64(predicate)
    }

    fun generateFromBase58(predicate: Base58String): MnemonicPhrase {
        return MnemonicPhrase.fromEntropyB58(predicate)
    }
}