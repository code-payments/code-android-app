package com.getcode.generator

import com.getcode.crypt.MnemonicPhrase
import com.getcode.solana.organizer.GiftCardAccount
import javax.inject.Inject

class GiftCardGenerator @Inject constructor(
): Generator<MnemonicPhrase?, GiftCardAccount> {
    override fun generate(predicate: MnemonicPhrase?): GiftCardAccount {
        return GiftCardAccount.newInstance(predicate)
    }
}