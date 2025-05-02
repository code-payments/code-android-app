package com.getcode.opencode.generators

import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.internal.generator.Generator
import com.getcode.opencode.model.accounts.GiftCardAccount
import javax.inject.Inject

class GiftCardGenerator @Inject constructor(): Generator<MnemonicPhrase?, GiftCardAccount> {
    override fun generate(predicate: MnemonicPhrase?): GiftCardAccount {
        return GiftCardAccount.create(predicate)
    }
}