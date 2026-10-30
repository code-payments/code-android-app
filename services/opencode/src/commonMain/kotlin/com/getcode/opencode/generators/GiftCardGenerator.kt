package com.getcode.opencode.generators

import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.internal.generator.Generator
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import javax.inject.Inject

class GiftCardGenerator @Inject constructor(): Generator<Pair<MnemonicPhrase?, Token>, GiftCardAccount> {
    override fun generate(predicate: Pair<MnemonicPhrase?, Token>): GiftCardAccount {
        val (mnemonic, token) = predicate
        return GiftCardAccount.create(token, mnemonic)
    }
}