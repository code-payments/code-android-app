package com.getcode.opencode.model.accounts

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint

class GiftCardAccount(
    val mnemonic: MnemonicPhrase,
    val cluster: AccountCluster,
) {
    var funded: Boolean = false
        private set

    fun fund() {
        funded = true
    }

    companion object {
        fun create(token: Token, mnemonic: MnemonicPhrase? = null): GiftCardAccount {
            val phrase = mnemonic ?: MnemonicPhrase.generate()
            return GiftCardAccount(
                mnemonic = phrase,
                cluster = AccountCluster.newInstance(
                    authority = DerivedKey.derive(DerivePath.primary, mnemonic = phrase),
                    token = token,
                )
            )
        }
    }
}

val GiftCardAccount.entropy: String
    get() = mnemonic.getBase58EncodedEntropy()
