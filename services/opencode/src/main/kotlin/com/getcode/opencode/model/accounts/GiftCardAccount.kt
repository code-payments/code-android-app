package com.getcode.opencode.model.accounts

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase

data class GiftCardAccount(
    val mnemonic: MnemonicPhrase,
    val cluster: AccountCluster,
) {
    companion object {
        fun create(mnemonic: MnemonicPhrase? = null): GiftCardAccount {
            val phrase = mnemonic ?: MnemonicPhrase.generate()
            return GiftCardAccount(
                mnemonic = phrase,
                cluster = AccountCluster.newInstance(
                    authority = DerivedKey.derive(DerivePath.primary, mnemonic = phrase)
                )
            )
        }
    }
}

val GiftCardAccount.entropy: String
    get() = mnemonic.getBase58EncodedEntropy()
