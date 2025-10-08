package com.getcode.opencode.managers

import com.getcode.crypt.MnemonicPhrase
import com.getcode.opencode.generators.GiftCardGenerator
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.Token
import javax.inject.Inject

class GiftCardManager @Inject constructor(
    private val generator: GiftCardGenerator
) {
    fun createGiftCard(mnemonic: MnemonicPhrase? = null, token: Token): GiftCardAccount {
        return generator.generate(mnemonic to token)
    }

    fun createGiftCardFromEntropy58(entropy: String, token: Token): GiftCardAccount {
        return createGiftCard(MnemonicPhrase.fromEntropyB58(entropy), token)
    }

    fun createGiftCardFromEntropy64(entropy: String, token: Token): GiftCardAccount {
        return createGiftCard(MnemonicPhrase.fromEntropyB64(entropy), token)
    }


    fun getEntropy(giftCard: GiftCardAccount): String {
        return giftCard.mnemonic.getBase58EncodedEntropy()
    }
}