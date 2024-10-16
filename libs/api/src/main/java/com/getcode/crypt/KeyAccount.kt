package com.getcode.crypt

class KeyAccount(
    val mnemonic: MnemonicPhrase,
    val derivedKey: DerivedKey,
    val tokenAccount: com.getcode.solana.keys.PublicKey
) {
    companion object {
        fun newInstance(
            mnemonic: MnemonicPhrase,
            derivedKey: DerivedKey,
            tokenAccount: com.getcode.solana.keys.PublicKey
        ): KeyAccount {
            return KeyAccount(
                mnemonic = mnemonic,
                derivedKey = derivedKey,
                tokenAccount = tokenAccount
            )
        }
    }
}