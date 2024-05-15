package com.getcode.manager

import android.content.Context
import com.getcode.crypt.MnemonicCode
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.generator.MnemonicGenerator
import com.getcode.utils.Base58String
import com.getcode.utils.Base64String
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MnemonicManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generator: MnemonicGenerator,
) {
    fun fromEntropyBase58(cashLink: Base58String): MnemonicPhrase {
        return generator.generateFromBase58(cashLink)
    }

    fun fromEntropyBase64(entropy: Base64String): MnemonicPhrase {
        return generator.generate(entropy)
    }

    fun getKeyPair(entropy: String): KeyPair {
        return fromEntropyBase64(entropy).getSolanaKeyPair(context)
    }

    fun getKeyPair(mnemonicPhrase: MnemonicPhrase): KeyPair {
        return mnemonicPhrase.getSolanaKeyPair(context)
    }

    fun getEncodedBase64(mnemonicPhrase: MnemonicPhrase): String {
        return mnemonicPhrase.getBase64EncodedEntropy(context)
    }

    fun getEncodedBase58(mnemonicPhrase: MnemonicPhrase): String {
        return mnemonicPhrase.getBase58EncodedEntropy(context)
    }

    val mnemonicCode: MnemonicCode = context.resources.let(::MnemonicCode)
}