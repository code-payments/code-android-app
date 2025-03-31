package com.getcode.manager

import com.getcode.crypt.MnemonicPhrase
import com.getcode.generator.GiftCardGenerator
import com.getcode.solana.organizer.GiftCardAccount
import javax.inject.Inject

class GiftCardManager @Inject constructor(
    private val generator: GiftCardGenerator
) {
    fun createGiftCard(mnemonic: MnemonicPhrase? = null): GiftCardAccount {
        return generator.generate(mnemonic)
    }

    fun getEntropy(giftCard: GiftCardAccount): String {
        return giftCard.mnemonicPhrase.getBase58EncodedEntropy()
    }
}