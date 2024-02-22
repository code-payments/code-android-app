package com.getcode.solana.organizer

import android.content.Context
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase

class GiftCardAccount(
    val mnemonicPhrase: MnemonicPhrase,
    val cluster: AccountCluster
) {
    companion object {
        fun newInstance(context: Context, mnemonicPhrase: MnemonicPhrase? = null): GiftCardAccount {
            val phrase = mnemonicPhrase ?: MnemonicPhrase.generate(context)

            return GiftCardAccount(
                mnemonicPhrase = phrase,
                cluster = AccountCluster.newInstance(
                    authority = DerivedKey.derive(
                        context = context,
                        path = DerivePath.primary,
                        mnemonic = phrase,
                    ),
                    kind = AccountCluster.Kind.Timelock,
                )
            )
        }
    }
}