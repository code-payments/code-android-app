package com.getcode.manager

import com.getcode.crypt.MnemonicCache
import com.getcode.crypt.MnemonicCode
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.generator.MnemonicGenerator
import com.getcode.utils.Base58String
import com.getcode.utils.Base64String
import javax.inject.Inject

class MnemonicManager @Inject constructor(
    private val generator: MnemonicGenerator,
) {
    fun fromEntropyBase58(cashLink: Base58String): MnemonicPhrase {
        return generator.generateFromBase58(cashLink)
    }

    fun fromEntropyBase64(entropy: Base64String): MnemonicPhrase {
        return generator.generate(entropy)
    }

    fun getKeyPair(entropy: String): KeyPair {
        return fromEntropyBase64(entropy).getSolanaKeyPair()
    }

    fun getKeyPair(mnemonicPhrase: MnemonicPhrase): KeyPair {
        return mnemonicPhrase.getSolanaKeyPair()
    }

    fun getEncodedBase64(mnemonicPhrase: MnemonicPhrase): String {
        return mnemonicPhrase.getBase64EncodedEntropy()
    }

    fun getEncodedBase58(mnemonicPhrase: MnemonicPhrase): String {
        return mnemonicPhrase.getBase58EncodedEntropy()
    }

    val mnemonicCode: MnemonicCode
        get() = MnemonicCache.cachedCode
}