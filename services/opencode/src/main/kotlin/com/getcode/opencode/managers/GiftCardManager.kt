package com.getcode.opencode.managers

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.generators.GiftCardGenerator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import javax.inject.Inject

class GiftCardManager @Inject constructor(
    private val generator: GiftCardGenerator
) {
    fun createGiftCard(mnemonic: MnemonicPhrase? = null): GiftCardAccount {
        return generator.generate(mnemonic)
    }

    fun createGiftCardFromEntropy58(entropy: String): GiftCardAccount {
        return createGiftCard(MnemonicPhrase.fromEntropyB58(entropy))
    }

    fun createGiftCardFromEntropy64(entropy: String): GiftCardAccount {
        return createGiftCard(MnemonicPhrase.fromEntropyB64(entropy))
    }


    fun getEntropy(giftCard: GiftCardAccount): String {
        return giftCard.mnemonic.getBase58EncodedEntropy()
    }
}