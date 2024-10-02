package com.getcode.crypt

import android.content.Context
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.keys.PublicKey

class KeyAccount(
    val mnemonic: MnemonicPhrase,
    val derivedKey: DerivedKey,
    val tokenAccount: PublicKey
) {
    companion object {
        fun newInstance(
            mnemonic: MnemonicPhrase,
            derivedKey: DerivedKey,
            tokenAccount: PublicKey
        ): KeyAccount {
            return KeyAccount(
                mnemonic = mnemonic,
                derivedKey = derivedKey,
                tokenAccount = tokenAccount
            )
        }
    }
}