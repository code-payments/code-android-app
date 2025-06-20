package com.getcode.opencode.model.accounts

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase

class PoolAccount(
    val mnemonic: MnemonicPhrase,
    val cluster: AccountCluster,
) {
    companion object {
        fun create(index: Long, mnemonic: MnemonicPhrase? = null): PoolAccount {
            val phrase = mnemonic ?: MnemonicPhrase.generate()
            return PoolAccount(
                mnemonic = phrase,
                cluster = AccountCluster.newInstance(
                    authority = DerivedKey.derive(DerivePath.getPool(index), mnemonic = phrase)
                )
            )
        }
    }
}

val PoolAccount.entropy: String
    get() = mnemonic.getBase58EncodedEntropy()