package com.getcode.solana.organizer

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase

class GiftCardAccount(
    val mnemonicPhrase: MnemonicPhrase,
    val cluster: AccountCluster
) {
    companion object {
        fun newInstance(mnemonicPhrase: MnemonicPhrase? = null): GiftCardAccount {
            val phrase = mnemonicPhrase ?: MnemonicPhrase.generate()

            return GiftCardAccount(
                mnemonicPhrase = phrase,
                cluster = AccountCluster.newInstance(
                    authority = DerivedKey.derive(
                        path = DerivePath.primary,
                        mnemonic = phrase,
                    ),
                    kind = AccountCluster.Kind.Timelock,
                )
            )
        }
    }
}