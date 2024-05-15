package com.getcode.manager

import android.content.Context
import com.getcode.crypt.MnemonicPhrase
import com.getcode.generator.Generator
import com.getcode.generator.GiftCardGenerator
import com.getcode.solana.organizer.GiftCardAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GiftCardManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generator: GiftCardGenerator
) {
    fun createGiftCard(mnemonic: MnemonicPhrase? = null): GiftCardAccount {
        return generator.generate(mnemonic)
    }

    fun getEntropy(giftCard: GiftCardAccount): String {
        return giftCard.mnemonicPhrase.getBase58EncodedEntropy(context)
    }
}