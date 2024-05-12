package com.getcode.manager

import android.content.Context
import com.getcode.crypt.MnemonicPhrase
import com.getcode.solana.organizer.GiftCardAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GiftCardManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createGiftCard(mnemonic: MnemonicPhrase? = null): GiftCardAccount {
        return GiftCardAccount.newInstance(context, mnemonic)
    }

    fun getEntropy(giftCard: GiftCardAccount): String {
        return giftCard.mnemonicPhrase.getBase58EncodedEntropy(context)
    }
}