package com.getcode.manager

import android.content.Context
import com.getcode.crypt.MnemonicCode
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MnemonicManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun fromCashLink(cashLink: String): MnemonicPhrase {
        return MnemonicPhrase.fromEntropyB58(context, cashLink)
    }

    fun fromEntropyBase64(entropy: String): MnemonicPhrase {
        return MnemonicPhrase.fromEntropyB64(context, entropy)
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

    val mnemonicCode: MnemonicCode = context.resources.let(::MnemonicCode)
}