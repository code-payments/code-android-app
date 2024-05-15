package com.getcode.generator

import android.content.Context
import com.getcode.crypt.MnemonicPhrase
import com.getcode.utils.Base58String
import com.getcode.utils.Base64String
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MnemonicGenerator @Inject constructor(
    @ApplicationContext private val context: Context
): Generator<Base64String, MnemonicPhrase> {

    override fun generate(predicate: Base64String): MnemonicPhrase {
        return MnemonicPhrase.fromEntropyB64(context, predicate)
    }

    fun generateFromBase58(predicate: Base58String): MnemonicPhrase {
        return MnemonicPhrase.fromEntropyB58(context, predicate)
    }
}