package com.getcode.opencode.mnemonic

import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.internal.generator.Generator
import com.getcode.opencode.utils.Base58String
import com.getcode.opencode.utils.Base64String
import javax.inject.Inject

class MnemonicGenerator @Inject constructor(): Generator<Base64String, MnemonicPhrase> {

    override fun generate(predicate: Base64String): MnemonicPhrase {
        return MnemonicPhrase.fromEntropyB64(predicate)
    }

    fun generateFromBase58(predicate: Base58String): MnemonicPhrase {
        return MnemonicPhrase.fromEntropyB58(predicate)
    }
}